package com.ryan.logisticexpress.dispatch.service;

import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ConflictException;
import com.ryan.logisticexpress.common.exception.ForbiddenException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.dispatch.dto.DispatchDtos.*;
import com.ryan.logisticexpress.dispatch.entity.DeliveryStop;
import com.ryan.logisticexpress.dispatch.entity.DispatchRoute;
import com.ryan.logisticexpress.dispatch.enums.RouteStatus;
import com.ryan.logisticexpress.dispatch.enums.StopStatus;
import com.ryan.logisticexpress.dispatch.repository.DeliveryStopRepository;
import com.ryan.logisticexpress.dispatch.repository.DispatchRouteRepository;
import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.hub.service.HubOperationService;
import com.ryan.logisticexpress.privacy.service.PhoneMaskingService;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.enums.FailureReasonCode;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.shipment.exceptions.InvalidShipmentStateTransitionException;
import com.ryan.logisticexpress.shipment.service.ShipmentService;
import com.ryan.logisticexpress.shipment.service.ShipmentStateMachine;
import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import com.ryan.logisticexpress.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class DispatchService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<StopStatus> ACTIVE_STOP_STATUSES = List.of(StopStatus.PENDING, StopStatus.ARRIVED);
    private static final List<RouteStatus> OPEN_ROUTE_STATUSES =
            List.of(RouteStatus.PLANNED, RouteStatus.IN_PROGRESS, RouteStatus.COMPLETED);
    private static final long MAX_POD_BYTES = 5L * 1024L * 1024L;
    private static final Map<String, String> ALLOWED_POD_TYPES = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            "image/webp", ".webp"
    );

    private final DispatchRouteRepository dispatchRouteRepository;
    private final DeliveryStopRepository deliveryStopRepository;
    private final ShipmentService shipmentService;
    private final ShipmentStateMachine shipmentStateMachine;
    private final HubOperationService hubOperationService;
    private final UserService userService;
    private final PhoneMaskingService phoneMaskingService;
    private final TransactionTemplate transactionTemplate;
    // ponytail: [NEEDS_CONFIRMATION] local filesystem storage for e-POD images; replace with S3/MinIO client when scaling beyond single node
    private final Path podStorageDir;

    @Autowired
    public DispatchService(
            DispatchRouteRepository dispatchRouteRepository,
            DeliveryStopRepository deliveryStopRepository,
            ShipmentService shipmentService,
            ShipmentStateMachine shipmentStateMachine,
            HubOperationService hubOperationService,
            UserService userService,
            PhoneMaskingService phoneMaskingService,
            TransactionTemplate transactionTemplate,
            @Value("${app.storage.pod-dir:uploads/pod}") String podStorageDir
    ) {
        this.dispatchRouteRepository = dispatchRouteRepository;
        this.deliveryStopRepository = deliveryStopRepository;
        this.shipmentService = shipmentService;
        this.shipmentStateMachine = shipmentStateMachine;
        this.hubOperationService = hubOperationService;
        this.userService = userService;
        this.phoneMaskingService = phoneMaskingService;
        this.transactionTemplate = transactionTemplate;
        this.podStorageDir = Path.of(podStorageDir).toAbsolutePath().normalize();
    }

    public record PodImageContent(byte[] bytes, MediaType mediaType) {
    }

    public DispatchRouteResponse assignManualRoute(AssignRouteRequest request, UUID actorUserId) {
        try {
            return transactionTemplate.execute(status -> assignManualRouteInTx(request, actorUserId));
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException(
                    "SHIPMENT_ALREADY_ASSIGNED",
                    "One or more shipments are already assigned to an active delivery route"
            );
        }
    }

    private DispatchRouteResponse assignManualRouteInTx(AssignRouteRequest request, UUID actorUserId) {
        User actor = userService.getUserEntityById(actorUserId);
        Hub hub = resolveActorHub(actor, request.hubId());

        User shipper = userService.getUserEntityById(request.shipperId());
        if (!shipper.hasRole(Role.ROLE_SHIPPER) || shipper.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("INVALID_SHIPPER", "Assigned user must be an active shipper");
        }
        if (shipper.getStaffProfile() != null && !hub.getId().equals(shipper.getStaffProfile().getHubId())) {
            throw new BadRequestException("SHIPPER_HUB_MISMATCH", "Shipper does not belong to destination hub");
        }

        List<UUID> orderedUniqueIds = request.shipmentIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (orderedUniqueIds.isEmpty()) {
            throw new BadRequestException("EMPTY_SHIPMENTS", "At least one shipment ID is required");
        }

        LocalDate routeDate = request.routeDate() != null ? request.routeDate() : LocalDate.now(VN_ZONE);
        DispatchRoute route = dispatchRouteRepository.saveAndFlush(DispatchRoute.builder()
                .optimizationBatchId(null)
                .hub(hub)
                .shipper(shipper)
                .routeDate(routeDate)
                .status(RouteStatus.IN_PROGRESS)
                .optimizedDistanceKm(BigDecimal.ZERO)
                .totalParcels(0)
                .totalWeightGram(0)
                .build());

        List<DeliveryStop> stops = new ArrayList<>();
        int totalWeight = 0;
        int seq = 1;
        String note = hasText(request.note()) ? request.note().trim() : "Assigned to shipper " + shipper.getUsername();

        for (UUID shipmentId : orderedUniqueIds) {
            Shipment shipment = shipmentService.getShipmentForUpdateOrThrow(shipmentId);
            if (shipment.getCurrentStatus() == ShipmentStatus.OUT_FOR_DELIVERY) {
                throw new ConflictException("SHIPMENT_ALREADY_ASSIGNED",
                        "Shipment " + shipment.getTrackingCode() + " is already out for delivery");
            }
            if (shipment.getCurrentStatus() != ShipmentStatus.AT_DEST_HUB) {
                throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.OUT_FOR_DELIVERY);
            }
            if (shipment.getDestHub() == null || !hub.getId().equals(shipment.getDestHub().getId())
                    || shipment.getCurrentHub() == null || !hub.getId().equals(shipment.getCurrentHub().getId())) {
                throw new BadRequestException("SHIPMENT_HUB_MISMATCH",
                        "Shipment " + shipment.getTrackingCode() + " is not located at hub " + hub.getCode());
            }

            shipment.assignDeliveryShipper(shipper);
            Shipment transitioned = shipmentStateMachine.validateAndTransition(
                    shipment,
                    ShipmentStatus.OUT_FOR_DELIVERY,
                    actor,
                    "OUT_FOR_DELIVERY",
                    note,
                    hub,
                    null,
                    null
            );

            DeliveryStop stop = deliveryStopRepository.saveAndFlush(DeliveryStop.builder()
                    .route(route)
                    .shipment(transitioned)
                    .stopSequence(seq++)
                    .timeWindowStartMin(transitioned.getDeliveryTimeWindowStart())
                    .timeWindowEndMin(transitioned.getDeliveryTimeWindowEnd())
                    .stopStatus(StopStatus.PENDING)
                    .build());
            stops.add(stop);
            totalWeight += transitioned.getActualWeightGram();
        }

        route.updateTotals(stops.size(), totalWeight);
        DispatchRoute savedRoute = dispatchRouteRepository.saveAndFlush(route);

        List<DeliveryStopResponse> stopResponses = stops.stream()
                .map(s -> DeliveryStopResponse.from(s, ShipmentResponse.from(s.getShipment())))
                .toList();
        return DispatchRouteResponse.from(savedRoute, stopResponses);
    }

    @Transactional(readOnly = true)
    public DispatchRouteResponse getRouteForOps(UUID routeId, UUID actorUserId) {
        DispatchRoute route = getRouteOrThrow(routeId);
        User actor = userService.getUserEntityById(actorUserId);
        assertHubAccess(actor, route.getHub());

        List<DeliveryStopResponse> stops = deliveryStopRepository.findByRouteIdOrderByStopSequenceAsc(routeId).stream()
                .map(s -> DeliveryStopResponse.from(s, ShipmentResponse.from(s.getShipment())))
                .toList();
        return DispatchRouteResponse.from(route, stops);
    }

    @Transactional(readOnly = true)
    public ShipperTasksResponse getShipperTasks(UUID shipperUserId, LocalDate routeDate) {
        LocalDate targetDate = routeDate != null ? routeDate : LocalDate.now(VN_ZONE);
        List<DispatchRoute> routes = dispatchRouteRepository
                .findByShipperIdAndRouteDateOrderByIdAsc(shipperUserId, targetDate);

        List<UUID> routeIds = routes.stream().map(DispatchRoute::getId).toList();
        Map<UUID, List<DeliveryStop>> stopsByRoute = new HashMap<>();
        if (!routeIds.isEmpty()) {
            for (DeliveryStop stop : deliveryStopRepository.findByRouteIdInOrderByStopSequenceAsc(routeIds)) {
                stopsByRoute.computeIfAbsent(stop.getRoute().getId(), k -> new ArrayList<>()).add(stop);
            }
        }

        List<DispatchRouteResponse> routeResponses = routes.stream()
                .map(route -> {
                    List<DeliveryStopResponse> stopDtos = stopsByRoute.getOrDefault(route.getId(), List.of()).stream()
                            .map(s -> DeliveryStopResponse.from(
                                    s,
                                    phoneMaskingService.maskForShipper(s.getShipment(), shipperUserId)
                            ))
                            .toList();
                    return DispatchRouteResponse.from(route, stopDtos);
                })
                .toList();

        List<ShipmentResponse> returnTasks = shipmentService
                .findShipmentsByDeliveryShipperAndStatuses(shipperUserId, List.of(ShipmentStatus.IN_RETURN_TRANSIT))
                .stream()
                .map(s -> phoneMaskingService.maskForShipper(s, shipperUserId))
                .toList();

        return new ShipperTasksResponse(targetDate, routeResponses, returnTasks);
    }

    @Transactional(readOnly = true)
    public DeliveryStopResponse getStopForShipper(UUID stopId, UUID shipperUserId) {
        DeliveryStop stop = getStopOrThrow(stopId);
        assertShipperOwnership(stop, shipperUserId);
        return DeliveryStopResponse.from(stop, phoneMaskingService.maskForShipper(stop.getShipment(), shipperUserId));
    }

    @Transactional
    public PodUploadResponse uploadPodImage(UUID stopId, MultipartFile file, UUID shipperUserId) {
        DeliveryStop stop = getStopOrThrow(stopId);
        assertShipperOwnership(stop, shipperUserId);

        if (!stop.getStopStatus().isActive() || stop.getShipment().getCurrentStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new ConflictException("STOP_NOT_ACTIVE", "POD can only be uploaded for an active OUT_FOR_DELIVERY stop");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("EMPTY_FILE", "POD image file is required");
        }
        if (file.getSize() > MAX_POD_BYTES) {
            throw new BadRequestException("FILE_TOO_LARGE", "POD image must not exceed 5MB");
        }

        String contentType = file.getContentType();
        String extension = contentType != null ? ALLOWED_POD_TYPES.get(contentType.toLowerCase(Locale.ROOT)) : null;
        if (extension == null) {
            throw new BadRequestException("INVALID_FILE_TYPE", "POD image must be JPEG, PNG, or WebP");
        }

        String filename = UUID.randomUUID() + extension;
        Path target = podStorageDir.resolve(filename).normalize();
        if (!target.startsWith(podStorageDir)) {
            throw new BadRequestException("INVALID_PATH", "Invalid storage path");
        }

        try {
            Files.createDirectories(podStorageDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store POD image", ex);
        }

        String url = "/api/pod/" + filename;
        stop.attachPodImage(url);
        deliveryStopRepository.save(stop);
        return new PodUploadResponse(stop.getId(), url);
    }

    @Transactional(readOnly = true)
    public PodImageContent loadPodImage(String filename, UUID actorUserId, boolean isOps) {
        if (!hasText(filename) || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("INVALID_FILENAME", "Invalid POD filename");
        }

        String expectedUrl = "/api/pod/" + filename;
        DeliveryStop stop = deliveryStopRepository.findFirstByPodImageUrl(expectedUrl)
                .orElseThrow(() -> new ResourceNotFoundException("POD_NOT_FOUND", "POD image not found"));

        if (!isOps) {
            boolean isAssignedShipper = stop.getRoute().getShipper().getId().equals(actorUserId);
            boolean isShopOwner = stop.getShipment().getShop().getId().equals(actorUserId);
            if (!isAssignedShipper && !isShopOwner) {
                throw new ForbiddenException("ACCESS_DENIED", "You do not have permission to view this POD image");
            }
        }

        Path path = podStorageDir.resolve(filename).normalize();
        if (!path.startsWith(podStorageDir) || !Files.exists(path)) {
            throw new ResourceNotFoundException("POD_NOT_FOUND", "POD file not found on storage");
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            MediaType mediaType = resolveMediaType(filename);
            return new PodImageContent(bytes, mediaType);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read POD image", ex);
        }
    }

    public DeliveryStopResponse confirmDelivery(UUID stopId, DeliverStopRequest request, UUID shipperUserId) {
        try {
            return transactionTemplate.execute(status -> confirmDeliveryInTx(stopId, request, shipperUserId));
        } catch (ObjectOptimisticLockingFailureException ex) {
            DeliveryStopResponse replayed = transactionTemplate.execute(status -> {
                DeliveryStop reloaded = getStopOrThrow(stopId);
                assertShipperOwnership(reloaded, shipperUserId);
                if (reloaded.getStopStatus() == StopStatus.DELIVERED
                        && reloaded.getShipment().getCurrentStatus() == ShipmentStatus.DELIVERED) {
                    return DeliveryStopResponse.from(
                            reloaded,
                            phoneMaskingService.maskForShipper(reloaded.getShipment(), shipperUserId)
                    );
                }
                return null;
            });
            if (replayed != null) {
                return replayed;
            }
            throw new ConflictException("CONCURRENT_DELIVERY_UPDATE", "Shipment was updated concurrently. Please retry.");
        }
    }

    private DeliveryStopResponse confirmDeliveryInTx(UUID stopId, DeliverStopRequest request, UUID shipperUserId) {
        DeliveryStop stop = getStopOrThrow(stopId);
        assertShipperOwnership(stop, shipperUserId);
        Shipment shipment = stop.getShipment();

        // Idempotent replay when already delivered by the same shipper
        if (stop.getStopStatus() == StopStatus.DELIVERED
                && shipment.getCurrentStatus() == ShipmentStatus.DELIVERED) {
            return DeliveryStopResponse.from(stop, phoneMaskingService.maskForShipper(shipment, shipperUserId));
        }

        if (!stop.getStopStatus().isActive() || shipment.getCurrentStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.DELIVERED);
        }

        User shipper = userService.getUserEntityById(shipperUserId);
        String podImageUrl = request != null ? request.podImageUrl() : null;
        String podSignature = request != null ? request.podReceiverSignature() : null;
        String note = (request != null && hasText(request.note())) ? request.note().trim() : "Delivered to receiver";

        Shipment delivered = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.DELIVERED,
                shipper,
                "DELIVERED",
                note,
                shipment.getCurrentHub(),
                request != null ? request.lat() : null,
                request != null ? request.lng() : null
        );

        stop.markDelivered(Instant.now(), podImageUrl, podSignature);
        DeliveryStop savedStop = deliveryStopRepository.saveAndFlush(stop);
        markRouteCompletedIfFinished(stop.getRoute());

        return DeliveryStopResponse.from(savedStop, phoneMaskingService.maskForShipper(delivered, shipperUserId));
    }

    @Transactional
    public DeliveryStopResponse reportDeliveryFailure(UUID stopId, FailStopRequest request, UUID shipperUserId) {
        DeliveryStop stop = getStopOrThrow(stopId);
        assertShipperOwnership(stop, shipperUserId);
        Shipment shipment = stop.getShipment();

        if (!stop.getStopStatus().isActive() || shipment.getCurrentStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.DELIVERY_FAILED);
        }

        User shipper = userService.getUserEntityById(shipperUserId);
        String reasonCode = request.reasonCode().name();
        String note = hasText(request.note()) ? request.note().trim() : "Delivery failed (" + reasonCode + ")";

        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.DELIVERY_FAILED,
                shipper,
                reasonCode,
                note,
                shipment.getCurrentHub(),
                request.lat(),
                request.lng()
        );

        stop.markFailed(Instant.now(), reasonCode);
        DeliveryStop savedStop = deliveryStopRepository.saveAndFlush(stop);
        markRouteCompletedIfFinished(stop.getRoute());

        return DeliveryStopResponse.from(savedStop, phoneMaskingService.maskForShipper(updated, shipperUserId));
    }

    @Transactional
    public DeliveryStopResponse reportDeliveryRefusal(UUID stopId, RefuseStopRequest request, UUID shipperUserId) {
        DeliveryStop stop = getStopOrThrow(stopId);
        assertShipperOwnership(stop, shipperUserId);
        Shipment shipment = stop.getShipment();

        if (!stop.getStopStatus().isActive() || shipment.getCurrentStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.REFUSED);
        }

        User shipper = userService.getUserEntityById(shipperUserId);
        FailureReasonCode code = (request != null && request.reasonCode() != null)
                ? request.reasonCode()
                : FailureReasonCode.RECEIVER_REFUSED;
        String note = (request != null && hasText(request.note()))
                ? request.note().trim()
                : "Receiver refused delivery (" + code.name() + ")";

        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.REFUSED,
                shipper,
                code.name(),
                note,
                shipment.getCurrentHub(),
                request != null ? request.lat() : null,
                request != null ? request.lng() : null
        );

        stop.markRefused(Instant.now(), code.name());
        DeliveryStop savedStop = deliveryStopRepository.saveAndFlush(stop);
        markRouteCompletedIfFinished(stop.getRoute());

        return DeliveryStopResponse.from(savedStop, phoneMaskingService.maskForShipper(updated, shipperUserId));
    }

    @Transactional
    public ShiftCloseResponse closeShift(CloseShiftRequest request, UUID actorUserId) {
        User actor = userService.getUserEntityById(actorUserId);
        Hub hub = resolveActorHub(actor, request.hubId());

        User shipper = userService.getUserEntityById(request.shipperId());
        if (!shipper.hasRole(Role.ROLE_SHIPPER)) {
            throw new BadRequestException("INVALID_SHIPPER", "Target user is not a shipper");
        }
        if (shipper.getStaffProfile() != null && !hub.getId().equals(shipper.getStaffProfile().getHubId())) {
            throw new BadRequestException("SHIPPER_HUB_MISMATCH", "Shipper does not belong to hub " + hub.getCode());
        }

        LocalDate routeDate = request.routeDate() != null ? request.routeDate() : LocalDate.now(VN_ZONE);
        Set<String> scannedCodes = normalizeCodes(request.returnedTrackingCodes());
        String baseNote = hasText(request.note()) ? request.note().trim() : "Returned to hub during shift close";

        List<String> returnedToHubCodes = new ArrayList<>();
        List<String> onHoldReturnedCodes = new ArrayList<>();

        for (String code : scannedCodes) {
            Shipment shipment = shipmentService.getShipmentByTrackingCodeForUpdateOrThrow(code);
            if (shipment.getAssignedDeliveryShipper() == null
                    || !shipment.getAssignedDeliveryShipper().getId().equals(shipper.getId())) {
                throw new ForbiddenException("SHIPPER_MISMATCH",
                        "Shipment " + code + " is not assigned to shipper " + shipper.getUsername());
            }
            if (shipment.getDestHub() == null || !hub.getId().equals(shipment.getDestHub().getId())) {
                throw new ForbiddenException("HUB_MISMATCH",
                        "Shipment " + code + " does not belong to hub " + hub.getCode());
            }

            ShipmentStatus current = shipment.getCurrentStatus();
            if (current == ShipmentStatus.OUT_FOR_DELIVERY) {
                shipment.updateCurrentHub(hub);
                shipmentStateMachine.validateAndTransition(
                        shipment,
                        ShipmentStatus.AT_DEST_HUB,
                        actor,
                        "SHIFT_RETURN_UNDELIVERED",
                        baseNote,
                        hub,
                        null,
                        null
                );
                deliveryStopRepository.findByShipmentIdAndStopStatusIn(shipment.getId(), ACTIVE_STOP_STATUSES)
                        .ifPresent(activeStop -> {
                            activeStop.markSkipped();
                            deliveryStopRepository.save(activeStop);
                        });
                returnedToHubCodes.add(code);
            } else if (current == ShipmentStatus.DELIVERY_FAILED) {
                if (shipment.getAttemptCount() >= ShipmentStateMachine.MAX_DELIVERY_ATTEMPTS) {
                    throw new InvalidShipmentStateTransitionException(
                            "Shipment " + code + " reached max delivery attempts and cannot return to AT_DEST_HUB");
                }
                shipment.updateCurrentHub(hub);
                shipmentStateMachine.validateAndTransition(
                        shipment,
                        ShipmentStatus.AT_DEST_HUB,
                        actor,
                        "SHIFT_RETURN_FAILED",
                        baseNote,
                        hub,
                        null,
                        null
                );
                returnedToHubCodes.add(code);
            } else if (current == ShipmentStatus.ON_HOLD_FOR_RETURN || current == ShipmentStatus.RETURN_INITIATED) {
                shipment.updateCurrentHub(hub);
                onHoldReturnedCodes.add(code);
            } else {
                throw new BadRequestException("INVALID_SHIFT_RETURN_STATUS",
                        "Shipment " + code + " cannot be scanned as shift return in status " + current);
            }
        }

        List<DispatchRoute> openRoutes = dispatchRouteRepository.findByShipperIdAndHubIdAndRouteDateAndStatusIn(
                shipper.getId(),
                hub.getId(),
                routeDate,
                OPEN_ROUTE_STATUSES
        );

        List<UUID> routeIds = openRoutes.stream().map(DispatchRoute::getId).toList();
        List<DeliveryStop> routeStops = routeIds.isEmpty()
                ? List.of()
                : deliveryStopRepository.findByRouteIdInOrderByStopSequenceAsc(routeIds);

        List<ShiftCodItem> codItems = new ArrayList<>();
        BigDecimal expectedCodTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<String> unreconciled = new ArrayList<>();

        for (DeliveryStop stop : routeStops) {
            Shipment shipment = stop.getShipment();
            ShipmentStatus st = shipment.getCurrentStatus();
            if (stop.getStopStatus().isActive() || st == ShipmentStatus.OUT_FOR_DELIVERY) {
                unreconciled.add(shipment.getTrackingCode());
            } else if (stop.getStopStatus() == StopStatus.DELIVERED && st == ShipmentStatus.DELIVERED) {
                expectedCodTotal = expectedCodTotal.add(shipment.getAmountToCollect());
                codItems.add(ShiftCodItem.from(shipment));
            } else if ((st == ShipmentStatus.DELIVERY_FAILED
                    || st == ShipmentStatus.ON_HOLD_FOR_RETURN
                    || st == ShipmentStatus.RETURN_INITIATED)
                    && !scannedCodes.contains(shipment.getTrackingCode())) {
                unreconciled.add(shipment.getTrackingCode());
            }
        }

        if (!unreconciled.isEmpty()) {
            throw new ConflictException(
                    "UNRECONCILED_PARCELS",
                    "All undelivered or failed parcels must be scanned back before closing shift: "
                            + String.join(", ", unreconciled)
            );
        }

        for (DispatchRoute route : openRoutes) {
            route.markHandedOver();
        }
        if (!openRoutes.isEmpty()) {
            dispatchRouteRepository.saveAll(openRoutes);
        }

        return new ShiftCloseResponse(
                shipper.getId(),
                hub.getId(),
                routeDate,
                routeIds,
                codItems.size(),
                returnedToHubCodes,
                onHoldReturnedCodes,
                expectedCodTotal.setScale(2, RoundingMode.HALF_UP),
                codItems
        );
    }

    private void markRouteCompletedIfFinished(DispatchRoute route) {
        if (route.getStatus() != RouteStatus.HANDED_OVER
                && !deliveryStopRepository.existsByRouteIdAndStopStatusIn(route.getId(), ACTIVE_STOP_STATUSES)) {
            route.markCompleted();
            dispatchRouteRepository.save(route);
        }
    }

    private DispatchRoute getRouteOrThrow(UUID routeId) {
        return dispatchRouteRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("ROUTE_NOT_FOUND", "Dispatch route not found: " + routeId));
    }

    private DeliveryStop getStopOrThrow(UUID stopId) {
        return deliveryStopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("STOP_NOT_FOUND", "Delivery stop not found: " + stopId));
    }

    private void assertShipperOwnership(DeliveryStop stop, UUID shipperUserId) {
        if (!stop.getRoute().getShipper().getId().equals(shipperUserId)) {
            throw new ForbiddenException("NOT_ASSIGNED_SHIPPER", "Delivery stop is not assigned to this shipper");
        }
        Shipment shipment = stop.getShipment();
        if (shipment.getAssignedDeliveryShipper() == null
                || !shipment.getAssignedDeliveryShipper().getId().equals(shipperUserId)) {
            throw new ForbiddenException("NOT_ASSIGNED_SHIPPER", "Shipment is not assigned to this shipper");
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
                normalized.add(c.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
