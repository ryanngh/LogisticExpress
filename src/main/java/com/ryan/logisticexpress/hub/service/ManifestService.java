package com.ryan.logisticexpress.hub.service;

import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ConflictException;
import com.ryan.logisticexpress.common.exception.ForbiddenException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.hub.dto.LinehaulDtos.*;
import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.hub.entity.Manifest;
import com.ryan.logisticexpress.hub.entity.ManifestItem;
import com.ryan.logisticexpress.hub.entity.Trip;
import com.ryan.logisticexpress.hub.enums.ManifestStatus;
import com.ryan.logisticexpress.hub.repository.ManifestItemRepository;
import com.ryan.logisticexpress.hub.repository.ManifestRepository;
import com.ryan.logisticexpress.hub.repository.TripRepository;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.shipment.exceptions.InvalidShipmentStateTransitionException;
import com.ryan.logisticexpress.shipment.service.ShipmentService;
import com.ryan.logisticexpress.shipment.service.ShipmentStateMachine;
import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManifestService {

    private static final String CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyMMdd");
    private static final List<ManifestStatus> ACTIVE_MANIFEST_STATUSES =
            List.of(ManifestStatus.OPEN, ManifestStatus.SEALED, ManifestStatus.IN_TRANSIT);
    private static final List<ManifestStatus> TERMINAL_MANIFEST_STATUSES =
            List.of(ManifestStatus.RECEIVED, ManifestStatus.DISCREPANCY);

    private final ManifestRepository manifestRepository;
    private final ManifestItemRepository manifestItemRepository;
    private final TripRepository tripRepository;
    private final HubOperationService hubOperationService;
    private final ShipmentService shipmentService;
    private final ShipmentStateMachine shipmentStateMachine;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;

    // E1: Transit hub scan — ONLY for IN_LINEHAUL parcels arriving at a hub != current_hub_id.
    // Uses PESSIMISTIC_WRITE lock on shipment row to prevent concurrent duplicate tracking events.
    @Transactional
    public ShipmentResponse scanTransitHub(TransitScanRequest request, UUID actorUserId) {
        User actor = userService.getUserEntityById(actorUserId);
        Hub transitHub = resolveActorHub(actor, request.hubId());

        Shipment shipment = shipmentService.getShipmentByTrackingCodeForUpdateOrThrow(request.trackingCode());
        if (shipment.getCurrentStatus() != ShipmentStatus.IN_LINEHAUL) {
            throw new InvalidShipmentStateTransitionException(
                    "E1 transit scan only applies to IN_LINEHAUL shipments (current: " + shipment.getCurrentStatus() + ")");
        }

        // Idempotent replay if already scanned at this transit hub
        if (shipment.getCurrentHub() != null && shipment.getCurrentHub().getId().equals(transitHub.getId())) {
            return ShipmentResponse.from(shipment);
        }

        shipment.updateCurrentHub(transitHub);
        String note = hasText(request.note())
                ? request.note().trim()
                : "Scanned at transit hub " + transitHub.getCode();
        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.IN_LINEHAUL,
                actor,
                "TRANSIT_HUB_SCAN",
                note,
                transitHub,
                null,
                null
        );
        return ShipmentResponse.from(updated);
    }

    // E2: Group outbound shipments at actor's hub by destination hub (dest_hub_id)
    @Transactional(readOnly = true)
    public List<SortGroupResponse> sortShipmentsByDestHub(UUID requestedHubId, UUID actorUserId) {
        User actor = userService.getUserEntityById(actorUserId);
        Hub hub = resolveActorHub(actor, requestedHubId);

        List<Shipment> sortable = shipmentService.findSortableShipmentsAtHub(hub.getId());
        Map<Hub, List<Shipment>> grouped = sortable.stream()
                .filter(s -> s.getDestHub() != null)
                .collect(Collectors.groupingBy(Shipment::getDestHub, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getCode()))
                .map(entry -> {
                    Hub dest = entry.getKey();
                    List<Shipment> list = entry.getValue();
                    int totalWeight = list.stream().mapToInt(Shipment::getActualWeightGram).sum();
                    return new SortGroupResponse(
                            dest.getId(),
                            dest.getCode(),
                            dest.getName(),
                            list.size(),
                            totalWeight,
                            list.stream().map(ShipmentResponse::from).toList()
                    );
                })
                .toList();
    }

    // E3: Create OPEN manifest and optionally scan initial parcels into it
    @Transactional
    public ManifestResponse createManifest(CreateManifestRequest request, UUID actorUserId) {
        User actor = userService.getUserEntityById(actorUserId);
        Hub originHub = resolveActorHub(actor, request.originHubId());
        Hub destHub = hubOperationService.getHubOrThrow(request.destHubId());

        if (originHub.getId().equals(destHub.getId())) {
            throw new BadRequestException("SAME_HUB", "Origin hub and destination hub must be different");
        }

        Manifest manifest = Manifest.builder()
                .code(generateUniqueManifestCode())
                .originHub(originHub)
                .destHub(destHub)
                .status(ManifestStatus.OPEN)
                .createdBy(actor)
                .build();
        Manifest saved = manifestRepository.saveAndFlush(manifest);

        if (request.trackingCodes() != null && !request.trackingCodes().isEmpty()) {
            Instant now = Instant.now();
            Set<String> uniqueCodes = normalizeCodes(request.trackingCodes());
            for (String code : uniqueCodes) {
                Shipment shipment = shipmentService.getShipmentByTrackingCodeOrThrow(code);
                validateShipmentForBagging(shipment, originHub);
                manifestItemRepository.saveAndFlush(ManifestItem.builder()
                        .manifest(saved)
                        .shipment(shipment)
                        .scanOutAt(now)
                        .missing(false)
                        .extra(false)
                        .build());
            }
        }

        return ManifestResponse.from(saved, manifestItemRepository.findByManifestId(saved.getId()));
    }

    // E3: Add a parcel to an OPEN manifest (DB unique uq_manifest_items_manifest_shipment enforces idempotency)
    public ManifestResponse addItemToManifest(UUID manifestId, AddManifestItemRequest request, UUID actorUserId) {
        try {
            return transactionTemplate.execute(status -> addItemInTx(manifestId, request.trackingCode(), actorUserId));
        } catch (DataIntegrityViolationException ex) {
            return transactionTemplate.execute(status -> getManifest(manifestId, actorUserId));
        }
    }

    private ManifestResponse addItemInTx(UUID manifestId, String trackingCode, UUID actorUserId) {
        Manifest manifest = getManifestOrThrow(manifestId);
        User actor = userService.getUserEntityById(actorUserId);
        assertHubAccess(actor, manifest.getOriginHub());

        if (manifest.getStatus() != ManifestStatus.OPEN) {
            throw new ConflictException("MANIFEST_LOCKED", "Cannot modify items of manifest in status " + manifest.getStatus());
        }

        Shipment shipment = shipmentService.getShipmentByTrackingCodeOrThrow(trackingCode);
        Optional<ManifestItem> existing = manifestItemRepository.findByManifestIdAndShipmentId(manifest.getId(), shipment.getId());
        if (existing.isPresent()) {
            return ManifestResponse.from(manifest, manifestItemRepository.findByManifestId(manifest.getId()));
        }

        validateShipmentForBagging(shipment, manifest.getOriginHub());
        manifestItemRepository.saveAndFlush(ManifestItem.builder()
                .manifest(manifest)
                .shipment(shipment)
                .scanOutAt(Instant.now())
                .missing(false)
                .extra(false)
                .build());

        return ManifestResponse.from(manifest, manifestItemRepository.findByManifestId(manifest.getId()));
    }

    // E3: Seal manifest (OPEN -> SEALED), stamp scan_out_at and lock item list
    @Transactional
    public ManifestResponse sealManifest(UUID manifestId, UUID actorUserId) {
        Manifest manifest = getManifestOrThrow(manifestId);
        User actor = userService.getUserEntityById(actorUserId);
        assertHubAccess(actor, manifest.getOriginHub());

        if (manifest.getStatus() != ManifestStatus.OPEN) {
            throw new ConflictException("INVALID_MANIFEST_STATUS", "Only OPEN manifests can be sealed");
        }

        List<ManifestItem> items = manifestItemRepository.findByManifestId(manifest.getId());
        if (items.isEmpty()) {
            throw new BadRequestException("EMPTY_MANIFEST", "Cannot seal an empty manifest");
        }

        Instant now = Instant.now();
        for (ManifestItem item : items) {
            item.ensureScanOutAt(now);
        }
        manifestItemRepository.saveAll(items);

        manifest.seal(now);
        Manifest saved = manifestRepository.saveAndFlush(manifest);
        return ManifestResponse.from(saved, items);
    }

    // E4: Dispatch trip from originHub -> destHub with one or more SEALED manifests.
    // Transitions all contained shipments to IN_LINEHAUL via State Machine (blocking unverified addresses).
    @Transactional
    public TripResponse dispatchTrip(DispatchTripRequest request, UUID actorUserId) {
        User actor = userService.getUserEntityById(actorUserId);
        Hub originHub = resolveActorHub(actor, request.originHubId());
        Hub destHub = hubOperationService.getHubOrThrow(request.destHubId());

        if (originHub.getId().equals(destHub.getId())) {
            throw new BadRequestException("SAME_HUB", "Origin hub and destination hub must be different");
        }

        List<UUID> uniqueManifestIds = request.manifestIds().stream().distinct().toList();
        List<Manifest> manifests = manifestRepository.findAllById(uniqueManifestIds);
        if (manifests.size() != uniqueManifestIds.size()) {
            throw new ResourceNotFoundException("MANIFEST_NOT_FOUND", "One or more manifests not found");
        }

        for (Manifest m : manifests) {
            if (m.getStatus() != ManifestStatus.SEALED) {
                throw new ConflictException("MANIFEST_NOT_SEALED", "Manifest " + m.getCode() + " must be SEALED before dispatch");
            }
            if (m.getTrip() != null) {
                throw new ConflictException("MANIFEST_ALREADY_DISPATCHED", "Manifest " + m.getCode() + " is already attached to a trip");
            }
            if (!m.getOriginHub().getId().equals(originHub.getId()) || !m.getDestHub().getId().equals(destHub.getId())) {
                throw new BadRequestException("MANIFEST_HUB_MISMATCH",
                        "Manifest " + m.getCode() + " origin/dest hubs do not match trip");
            }
        }

        Instant now = Instant.now();
        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .code(generateUniqueTripCode())
                .originHub(originHub)
                .destHub(destHub)
                .vehiclePlate(hasText(request.vehiclePlate()) ? request.vehiclePlate().trim() : null)
                .driverName(hasText(request.driverName()) ? request.driverName().trim() : null)
                .status(Trip.Status.IN_TRANSIT)
                .departedAt(now)
                .build());

        for (Manifest manifest : manifests) {
            manifest.dispatchOnTrip(trip);
            manifestRepository.save(manifest);

            List<ManifestItem> items = manifestItemRepository.findByManifestId(manifest.getId());
            for (ManifestItem item : items) {
                Shipment shipment = item.getShipment();
                assertAddressesVerified(shipment);
                shipmentStateMachine.validateAndTransition(
                        shipment,
                        ShipmentStatus.IN_LINEHAUL,
                        actor,
                        "LINEHAUL_DISPATCH",
                        "Dispatched on trip " + trip.getCode() + " (manifest " + manifest.getCode() + ")",
                        originHub,
                        null,
                        null
                );
            }
        }

        return TripResponse.from(trip, manifests.stream().map(Manifest::getId).toList());
    }

    // E5: Destination hub receives manifest, reconciles scanned codes vs manifest_items.
    // Matched -> scan_in_at + IN_LINEHAUL -> AT_DEST_HUB (or IN_LINEHAUL if intermediate hub), NO duplicate E1 event.
    // Missing -> is_missing = TRUE, shipment stays IN_LINEHAUL.
    // Extra -> INSERT new manifest_items (is_extra = TRUE, scan_in_at = now), do NOT change shipment status.
    // Any missing/extra -> manifest status = DISCREPANCY, else RECEIVED.
    @Transactional
    public ManifestResponse receiveManifest(UUID manifestId, ReceiveManifestRequest request, UUID actorUserId) {
        Manifest manifest = getManifestOrThrow(manifestId);
        User actor = userService.getUserEntityById(actorUserId);
        Hub destHub = manifest.getDestHub();
        assertHubAccess(actor, destHub);

        if (manifest.getStatus() != ManifestStatus.IN_TRANSIT) {
            throw new ConflictException("INVALID_MANIFEST_STATUS",
                    "Only IN_TRANSIT manifests can be received (current: " + manifest.getStatus() + ")");
        }

        Set<String> scannedCodes = normalizeCodes(request.scannedTrackingCodes());
        List<ManifestItem> items = new ArrayList<>(manifestItemRepository.findByManifestId(manifest.getId()));
        Set<String> expectedCodes = new HashSet<>();

        Instant now = Instant.now();
        boolean hasDiscrepancy = false;
        String baseNote = hasText(request.note())
                ? request.note().trim()
                : "Received via manifest " + manifest.getCode() + " at " + destHub.getCode();

        for (ManifestItem item : items) {
            Shipment shipment = item.getShipment();
            String code = shipment.getTrackingCode();
            expectedCodes.add(code);

            if (scannedCodes.contains(code)) {
                item.markScannedIn(now);
                shipment.updateCurrentHub(destHub);
                boolean reachedFinalDest = shipment.getDestHub() != null
                        && shipment.getDestHub().getId().equals(destHub.getId());
                ShipmentStatus targetStatus = reachedFinalDest
                        ? ShipmentStatus.AT_DEST_HUB
                        : ShipmentStatus.IN_LINEHAUL;
                shipmentStateMachine.validateAndTransition(
                        shipment,
                        targetStatus,
                        actor,
                        "MANIFEST_RECEIVED",
                        baseNote,
                        destHub,
                        null,
                        null
                );
            } else {
                // ponytail: [NEEDS_CONFIRMATION] missing parcel keeps IN_LINEHAUL; add LOST/DAMAGED transition if E7 requires it
                item.markMissing();
                hasDiscrepancy = true;
            }
        }
        manifestItemRepository.saveAll(items);

        // Extra parcels not listed in manifest: INSERT manifest_items(is_extra = true) without changing shipment status
        for (String scannedCode : scannedCodes) {
            if (!expectedCodes.contains(scannedCode)) {
                Shipment extraShipment = shipmentService.getShipmentByTrackingCodeOrThrow(scannedCode);
                ManifestItem extraItem = manifestItemRepository.saveAndFlush(ManifestItem.builder()
                        .manifest(manifest)
                        .shipment(extraShipment)
                        .scanOutAt(null)
                        .scanInAt(now)
                        .missing(false)
                        .extra(true)
                        .build());
                items.add(extraItem);
                hasDiscrepancy = true;
            }
        }

        manifest.markReceived(actor, hasDiscrepancy, now);
        Manifest saved = manifestRepository.saveAndFlush(manifest);

        if (saved.getTrip() != null
                && !manifestRepository.existsByTripIdAndStatusNotIn(saved.getTrip().getId(), TERMINAL_MANIFEST_STATUSES)) {
            saved.getTrip().markArrived(now);
            tripRepository.save(saved.getTrip());
        }

        return ManifestResponse.from(saved, items);
    }

    @Transactional(readOnly = true)
    public ManifestResponse getManifest(UUID manifestId, UUID actorUserId) {
        Manifest manifest = getManifestOrThrow(manifestId);
        User actor = userService.getUserEntityById(actorUserId);
        if (!actor.hasRole(Role.ROLE_ADMIN) && actor.getStaffProfile() != null) {
            UUID staffHubId = actor.getStaffProfile().getHubId();
            if (!manifest.getOriginHub().getId().equals(staffHubId)
                    && !manifest.getDestHub().getId().equals(staffHubId)) {
                throw new ForbiddenException("HUB_ACCESS_DENIED", "You do not have access to this manifest");
            }
        }
        return ManifestResponse.from(manifest, manifestItemRepository.findByManifestId(manifest.getId()));
    }

    private Manifest getManifestOrThrow(UUID manifestId) {
        return manifestRepository.findById(manifestId)
                .orElseThrow(() -> new ResourceNotFoundException("MANIFEST_NOT_FOUND", "Manifest not found: " + manifestId));
    }

    private void validateShipmentForBagging(Shipment shipment, Hub originHub) {
        if (shipment.getCurrentStatus() != ShipmentStatus.AT_ORIGIN_HUB
                && shipment.getCurrentStatus() != ShipmentStatus.IN_LINEHAUL) {
            throw new BadRequestException("INVALID_SHIPMENT_STATUS",
                    "Shipment " + shipment.getTrackingCode() + " must be AT_ORIGIN_HUB or IN_LINEHAUL to be bagged");
        }
        if (shipment.getCurrentHub() == null || !shipment.getCurrentHub().getId().equals(originHub.getId())) {
            throw new BadRequestException("SHIPMENT_NOT_AT_HUB",
                    "Shipment " + shipment.getTrackingCode() + " is not currently at hub " + originHub.getCode());
        }
        if (manifestItemRepository.existsByShipmentIdAndManifestStatusIn(shipment.getId(), ACTIVE_MANIFEST_STATUSES)) {
            throw new ConflictException("SHIPMENT_ALREADY_MANIFESTED",
                    "Shipment " + shipment.getTrackingCode() + " is already in an active manifest");
        }
    }

    private void assertAddressesVerified(Shipment shipment) {
        if ((shipment.getPickupAddress() != null && shipment.getPickupAddress().getNormalizationStatus() != NormalizationStatus.VERIFIED)
                || (shipment.getDeliveryAddress() != null && shipment.getDeliveryAddress().getNormalizationStatus() != NormalizationStatus.VERIFIED)) {
            throw new InvalidShipmentStateTransitionException(
                    "Shipment " + shipment.getTrackingCode() + " addresses must be VERIFIED before entering IN_LINEHAUL");
        }
    }

    private Hub resolveActorHub(User actor, UUID requestedHubId) {
        UUID staffHubId = actor.getStaffProfile() != null ? actor.getStaffProfile().getHubId() : null;
        if (staffHubId != null && !actor.hasRole(Role.ROLE_ADMIN)) {
            if (requestedHubId != null && !requestedHubId.equals(staffHubId)) {
                throw new ForbiddenException("HUB_ACCESS_DENIED", "Staff can only operate at their assigned hub");
            }
            return hubOperationService.getHubOrThrow(staffHubId);
        }
        UUID targetHubId = requestedHubId != null ? requestedHubId : staffHubId;
        if (targetHubId == null) {
            throw new BadRequestException("MISSING_HUB", "Hub ID is required");
        }
        return hubOperationService.getHubOrThrow(targetHubId);
    }

    private void assertHubAccess(User actor, Hub requiredHub) {
        if (actor.hasRole(Role.ROLE_ADMIN) || requiredHub == null) {
            return;
        }
        // ponytail: [NEEDS_CONFIRMATION] strict staffHubId == requiredHub.id; extend here if REGIONAL_HUB staff may act on child LOCAL_POST_OFFICE
        if (actor.getStaffProfile() == null || !requiredHub.getId().equals(actor.getStaffProfile().getHubId())) {
            throw new ForbiddenException("HUB_ACCESS_DENIED", "You do not have access to hub " + requiredHub.getCode());
        }
    }

    private Set<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String c : codes) {
            if (hasText(c)) {
                normalized.add(c.trim().toUpperCase());
            }
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String generateUniqueManifestCode() {
        return generateUniqueCode("MF", manifestRepository::existsByCode);
    }

    private String generateUniqueTripCode() {
        return generateUniqueCode("TR", tripRepository::existsByCode);
    }

    private String generateUniqueCode(String prefix, java.util.function.Predicate<String> existsCheck) {
        String datePart = DATE_PREFIX.format(LocalDate.now(VN_ZONE));
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(prefix).append(datePart);
            for (int j = 0; j < 6; j++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!existsCheck.test(code)) {
                return code;
            }
        }
        return prefix + datePart + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
