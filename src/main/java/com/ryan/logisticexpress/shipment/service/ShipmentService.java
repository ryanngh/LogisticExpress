package com.ryan.logisticexpress.shipment.service;

import com.ryan.logisticexpress.address.dto.CreateVerifiedAddressRequest;
import com.ryan.logisticexpress.address.entity.Address;
import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import com.ryan.logisticexpress.address.service.AddressService;
import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ForbiddenException;
import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.hub.service.HubOperationService;
import com.ryan.logisticexpress.pricing.entity.RateCard;
import com.ryan.logisticexpress.pricing.entity.ServiceType;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.pricing.enums.ZoneType;
import com.ryan.logisticexpress.pricing.service.PricingEngineService;
import com.ryan.logisticexpress.shipment.dto.*;
import com.ryan.logisticexpress.shipment.dto.PickupIntakeDtos.*;
import com.ryan.logisticexpress.shipment.dto.ShopActionDtos.*;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.entity.TrackingEvent;
import com.ryan.logisticexpress.shipment.enums.FailureReasonCode;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.shipment.exceptions.InvalidShipmentStateTransitionException;
import com.ryan.logisticexpress.shipment.exceptions.ShipmentNotFoundException;
import com.ryan.logisticexpress.shipment.repository.ShipmentRepository;
import com.ryan.logisticexpress.shipment.repository.TrackingEventRepository;
import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import com.ryan.logisticexpress.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final String CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_PREFIX_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Set<ShipmentStatus> COD_ADJUSTABLE_STATUSES = EnumSet.of(
            ShipmentStatus.CREATED,
            ShipmentStatus.PICKUP_ASSIGNED,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.AT_ORIGIN_HUB,
            ShipmentStatus.IN_LINEHAUL,
            ShipmentStatus.AT_DEST_HUB
    );
    // ponytail: [NEEDS_CONFIRMATION] early shop return permitted from AT_DEST_HUB and DELIVERY_FAILED in addition to ON_HOLD_FOR_RETURN; tighten or expand when return policy is finalized
    private static final Set<ShipmentStatus> SHOP_RETURNABLE_STATUSES = EnumSet.of(
            ShipmentStatus.ON_HOLD_FOR_RETURN,
            ShipmentStatus.AT_DEST_HUB,
            ShipmentStatus.DELIVERY_FAILED
    );

    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final ShipmentStateMachine shipmentStateMachine;
    private final BarcodeService barcodeService;
    private final PricingEngineService pricingEngineService;
    private final AddressService addressService;
    private final HubOperationService hubOperationService;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;

    public ShipmentResponse createShipment(CreateShipmentRequest request, UUID shopUserId) {
        if (request.deliveryTimeWindowStart() != null && request.deliveryTimeWindowEnd() != null
                && request.deliveryTimeWindowEnd() < request.deliveryTimeWindowStart()) {
            throw new BadRequestException("INVALID_TIME_WINDOW", "Delivery time window end must be >= start");
        }

        String idempotencyKey = (request.idempotencyKey() != null && !request.idempotencyKey().isBlank())
                ? request.idempotencyKey().trim()
                : null;

        try {
            return transactionTemplate.execute(status -> createShipmentInTx(request, shopUserId, idempotencyKey));
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey != null) {
                ShipmentResponse existing = transactionTemplate.execute(status ->
                        shipmentRepository.findByShopIdAndIdempotencyKey(shopUserId, idempotencyKey)
                                .map(ShipmentResponse::from)
                                .orElse(null)
                );
                if (existing != null) {
                    return existing;
                }
            }
            throw ex;
        }
    }

    private ShipmentResponse createShipmentInTx(CreateShipmentRequest request, UUID shopUserId, String idempotencyKey) {
        User shop = userService.getUserEntityById(shopUserId);
        if (!shop.hasRole(Role.ROLE_SHOP)) {
            throw new ForbiddenException("NOT_A_SHOP", "Only shop accounts can create shipments");
        }

        if (idempotencyKey != null) {
            Optional<Shipment> existing = shipmentRepository.findByShopIdAndIdempotencyKey(shop.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return ShipmentResponse.from(existing.get());
            }
        }

        Address pickupAddress = resolveAddress(request.pickupAddressId(), request.pickupAddress(), shopUserId, "Pickup");
        Address deliveryAddress = resolveAddress(request.deliveryAddressId(), request.deliveryAddress(), shopUserId, "Delivery");

        Hub originHub = resolveHubFromAddress(pickupAddress);
        Hub destHub = resolveHubFromAddress(deliveryAddress);

        ServiceType serviceType = pricingEngineService.getServiceTypeEntityByCode(request.serviceTypeCode());
        ZoneType zoneType = pricingEngineService.determineZoneType(
                pickupAddress.getProvince().getRegion(),
                pickupAddress.getProvince().getCode(),
                deliveryAddress.getProvince().getRegion(),
                deliveryAddress.getProvince().getCode()
        );

        RateCard rateCard = pricingEngineService.findActiveRateCard(serviceType.getId(), zoneType);

        int volumetric = pricingEngineService.calculateVolumetricWeight(
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                serviceType.getVolumetricDivisor()
        );
        int chargeableWeight = pricingEngineService.determineChargeableWeight(request.actualWeightGram(), volumetric);

        BigDecimal baseFee = pricingEngineService.calculateBaseFee(rateCard, chargeableWeight);
        BigDecimal declaredValue = request.declaredValue() != null ? request.declaredValue() : BigDecimal.ZERO;
        BigDecimal insuranceFee = pricingEngineService.calculateInsuranceFee(declaredValue);
        BigDecimal shippingFee = pricingEngineService.calculateShippingFee(baseFee, insuranceFee);
        BigDecimal returnFee = pricingEngineService.calculateReturnFee(baseFee, rateCard.getReturnFeeRatio());
        BigDecimal codAmount = request.codAmount() != null ? request.codAmount() : BigDecimal.ZERO;
        FeePayer feePayer = request.feePayer();
        BigDecimal amountToCollect = pricingEngineService.calculateAmountToCollect(codAmount, shippingFee, feePayer);

        Instant slaDeadline = serviceType.getSlaHours() != null && serviceType.getSlaHours() > 0
                ? Instant.now().plus(Duration.ofHours(serviceType.getSlaHours()))
                : null;

        String trackingCode = generateUniqueTrackingCode();

        Shipment shipment = Shipment.builder()
                .trackingCode(trackingCode)
                .shop(shop)
                .serviceType(serviceType)
                .rateCard(rateCard)
                .currentStatus(ShipmentStatus.CREATED)
                .originHub(originHub)
                .destHub(destHub)
                .currentHub(null)
                .senderName(request.senderName().trim())
                .senderPhone(request.senderPhone().trim())
                .pickupAddress(pickupAddress)
                .receiverName(request.receiverName().trim())
                .receiverPhone(request.receiverPhone().trim())
                .deliveryAddress(deliveryAddress)
                .actualWeightGram(request.actualWeightGram())
                .lengthCm(request.lengthCm())
                .widthCm(request.widthCm())
                .heightCm(request.heightCm())
                .chargeableWeightGram(chargeableWeight)
                .declaredValue(declaredValue)
                .codAmount(codAmount)
                .baseFee(baseFee)
                .shippingFee(shippingFee)
                .insuranceFee(insuranceFee)
                .returnFee(returnFee)
                .feePayer(feePayer)
                .amountToCollect(amountToCollect)
                .deliveryTimeWindowStart(request.deliveryTimeWindowStart())
                .deliveryTimeWindowEnd(request.deliveryTimeWindowEnd())
                .scheduledDeliveryDate(request.scheduledDeliveryDate())
                .pickupAttemptCount(0)
                .attemptCount(0)
                .rescueCount(0)
                .slaDeadline(slaDeadline)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .build();

        Shipment saved = shipmentRepository.saveAndFlush(shipment);

        TrackingEvent initialEvent = TrackingEvent.builder()
                .shipment(saved)
                .status(ShipmentStatus.CREATED)
                .actor(shop)
                .hub(originHub)
                .note("Shipment created")
                .occurredAt(Instant.now())
                .build();
        trackingEventRepository.save(initialEvent);

        return ShipmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> searchShipments(
            UUID shopId,
            ShipmentStatus status,
            String trackingCode,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        Specification<Shipment> spec = (root, query, cb) -> cb.conjunction();

        if (shopId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("shop").get("id"), shopId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currentStatus"), status));
        }
        if (trackingCode != null && !trackingCode.isBlank()) {
            String clean = trackingCode.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("trackingCode")), "%" + clean + "%"));
        }
        if (fromDate != null) {
            Instant from = fromDate.atStartOfDay(VN_ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (toDate != null) {
            Instant to = toDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }

        return shipmentRepository.findAll(spec, pageable).map(ShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentOrThrow(UUID id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ShipmentNotFoundException(id.toString()));
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByTrackingCodeOrThrow(String trackingCode) {
        return shipmentRepository.findByTrackingCode(trackingCode.trim().toUpperCase())
                .orElseThrow(() -> new ShipmentNotFoundException(trackingCode));
    }

    @Transactional
    public Shipment getShipmentForUpdateOrThrow(UUID id) {
        return shipmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ShipmentNotFoundException(id.toString()));
    }

    @Transactional
    public Shipment getShipmentByTrackingCodeForUpdateOrThrow(String trackingCode) {
        return shipmentRepository.findByTrackingCodeForUpdate(trackingCode.trim().toUpperCase())
                .orElseThrow(() -> new ShipmentNotFoundException(trackingCode));
    }

    @Transactional(readOnly = true)
    public List<Shipment> findShipmentsByDeliveryShipperAndStatuses(UUID shipperId, Collection<ShipmentStatus> statuses) {
        return shipmentRepository.findByAssignedDeliveryShipperIdAndCurrentStatusIn(shipperId, statuses);
    }

    @Transactional(readOnly = true)
    public List<Shipment> findSortableShipmentsAtHub(UUID hubId) {
        return shipmentRepository.findByCurrentHubIdAndCurrentStatusInAndDestHubIdNot(
                hubId,
                List.of(ShipmentStatus.AT_ORIGIN_HUB, ShipmentStatus.IN_LINEHAUL),
                hubId
        );
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(UUID shipmentId, UUID currentUserId, boolean isOps) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        if (!isOps && (currentUserId == null || !shipment.getShop().getId().equals(currentUserId))) {
            throw new ForbiddenException("ACCESS_DENIED", "You do not own this shipment");
        }
        return ShipmentResponse.from(shipment);
    }

    @Transactional
    public ShipmentResponse updateShipment(
            UUID shipmentId,
            UpdateShipmentRequest request,
            UUID actorUserId,
            boolean isOps
    ) {
        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (!isOps && !shipment.getShop().getId().equals(actorUserId)) {
            throw new ForbiddenException("ACCESS_DENIED", "You do not own this shipment");
        }

        if (shipment.getCurrentStatus() != ShipmentStatus.CREATED
                && shipment.getCurrentStatus() != ShipmentStatus.PICKUP_ASSIGNED) {
            throw new InvalidShipmentStateTransitionException("Cannot update shipment once picked up");
        }

        Address deliveryAddress = shipment.getDeliveryAddress();
        if (request.deliveryAddressId() != null || request.deliveryAddress() != null) {
            deliveryAddress = resolveAddress(request.deliveryAddressId(), request.deliveryAddress(), actorUserId, "Delivery");
        }

        Hub destHub = resolveHubFromAddress(deliveryAddress);

        ZoneType zoneType = pricingEngineService.determineZoneType(
                shipment.getPickupAddress().getProvince().getRegion(),
                shipment.getPickupAddress().getProvince().getCode(),
                deliveryAddress.getProvince().getRegion(),
                deliveryAddress.getProvince().getCode()
        );

        RateCard rateCard = pricingEngineService.findActiveRateCard(shipment.getServiceType().getId(), zoneType);

        int volumetric = pricingEngineService.calculateVolumetricWeight(
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                shipment.getServiceType().getVolumetricDivisor()
        );
        int chargeableWeight = pricingEngineService.determineChargeableWeight(request.actualWeightGram(), volumetric);

        BigDecimal baseFee = pricingEngineService.calculateBaseFee(rateCard, chargeableWeight);
        BigDecimal declaredValue = request.declaredValue() != null ? request.declaredValue() : BigDecimal.ZERO;
        BigDecimal insuranceFee = pricingEngineService.calculateInsuranceFee(declaredValue);
        BigDecimal shippingFee = pricingEngineService.calculateShippingFee(baseFee, insuranceFee);
        BigDecimal returnFee = pricingEngineService.calculateReturnFee(baseFee, rateCard.getReturnFeeRatio());
        BigDecimal codAmount = request.codAmount() != null ? request.codAmount() : BigDecimal.ZERO;
        FeePayer feePayer = request.feePayer();
        BigDecimal amountToCollect = pricingEngineService.calculateAmountToCollect(codAmount, shippingFee, feePayer);

        shipment.updateEditableDetails(
                request.receiverName().trim(),
                request.receiverPhone().trim(),
                deliveryAddress,
                destHub,
                request.actualWeightGram(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                chargeableWeight,
                declaredValue,
                codAmount,
                rateCard,
                baseFee,
                shippingFee,
                insuranceFee,
                returnFee,
                feePayer,
                amountToCollect
        );

        return ShipmentResponse.from(shipmentRepository.save(shipment));
    }

    @Transactional
    public ShipmentResponse cancelShipment(UUID shipmentId, String reason, UUID actorUserId, boolean isOps) {
        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (!isOps && !shipment.getShop().getId().equals(actorUserId)) {
            throw new ForbiddenException("ACCESS_DENIED", "You do not own this shipment");
        }

        if (shipment.getCurrentStatus() != ShipmentStatus.CREATED
                && shipment.getCurrentStatus() != ShipmentStatus.PICKUP_ASSIGNED) {
            throw new InvalidShipmentStateTransitionException("Cannot cancel shipment once picked up");
        }

        User actor = userService.getUserEntityById(actorUserId);
        Shipment cancelled = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.CANCELLED,
                actor,
                "CANCELLED",
                reason,
                shipment.getCurrentHub(),
                null,
                null
        );

        return ShipmentResponse.from(cancelled);
    }

    @Transactional(readOnly = true)
    public ShipmentDetailResponse getShipmentDetail(String trackingCode, UUID currentUserId, boolean isOps) {
        Shipment shipment = getShipmentByTrackingCodeOrThrow(trackingCode);

        if (!isOps && (currentUserId == null || !shipment.getShop().getId().equals(currentUserId))) {
            throw new ForbiddenException("ACCESS_DENIED", "You do not have permission to view this shipment");
        }

        String barcode = barcodeService.generateBarcodeBase64(shipment.getTrackingCode(), 300, 75);
        String qr = barcodeService.generateQrCodeBase64("TRACK:" + shipment.getTrackingCode(), 160, 160);

        List<TrackingEventResponse> events = trackingEventRepository
                .findByShipmentIdOrderByOccurredAtAsc(shipment.getId()).stream()
                .map(TrackingEventResponse::from)
                .toList();

        return new ShipmentDetailResponse(ShipmentResponse.from(shipment), events, barcode, qr);
    }

    @Transactional
    public ShipmentResponse assignPickupShipper(UUID shipmentId, AssignPickupRequest request, UUID actorUserId) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        if (shipment.getCurrentStatus() != ShipmentStatus.CREATED) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.PICKUP_ASSIGNED);
        }

        User actor = userService.getUserEntityById(actorUserId);
        assertHubAccess(actor, shipment.getOriginHub());

        User shipper = userService.getUserEntityById(request.shipperId());
        if (!shipper.hasRole(Role.ROLE_SHIPPER) || shipper.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("INVALID_SHIPPER", "Assigned user must be an active shipper");
        }
        if (shipper.getStaffProfile() != null && shipment.getOriginHub() != null
                && !shipment.getOriginHub().getId().equals(shipper.getStaffProfile().getHubId())) {
            throw new BadRequestException("SHIPPER_HUB_MISMATCH", "Shipper does not belong to shipment origin hub");
        }

        shipment.assignPickupShipper(shipper);
        String note = hasText(request.note()) ? request.note().trim() : "Pickup shipper assigned";
        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.PICKUP_ASSIGNED,
                actor,
                "PICKUP_ASSIGNED",
                note,
                shipment.getOriginHub(),
                null,
                null
        );
        return ShipmentResponse.from(updated);
    }

    @Transactional
    public ShipmentResponse recordPickupResult(UUID shipmentId, PickupResultRequest request, UUID shipperUserId) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        if (shipment.getAssignedPickupShipper() == null
                || !shipment.getAssignedPickupShipper().getId().equals(shipperUserId)) {
            throw new ForbiddenException("NOT_ASSIGNED_SHIPPER", "Shipment is not assigned to this shipper for pickup");
        }

        User shipper = userService.getUserEntityById(shipperUserId);
        if (Boolean.TRUE.equals(request.success())) {
            String note = hasText(request.note()) ? request.note().trim() : "Package picked up by shipper";
            Shipment updated = shipmentStateMachine.validateAndTransition(
                    shipment,
                    ShipmentStatus.PICKED_UP,
                    shipper,
                    "PICKED_UP",
                    note,
                    shipment.getOriginHub(),
                    request.lat(),
                    request.lng()
            );
            return ShipmentResponse.from(updated);
        }

        String reasonCode = request.reasonCode() != null ? request.reasonCode().name() : FailureReasonCode.OTHER.name();
        String note = hasText(request.note()) ? request.note().trim() : "Pickup failed (" + reasonCode + ")";
        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.PICKUP_ASSIGNED,
                shipper,
                reasonCode,
                note,
                shipment.getOriginHub(),
                request.lat(),
                request.lng()
        );
        return ShipmentResponse.from(updated);
    }

    @Transactional
    public ShipmentResponse intakeAtOriginHub(IntakeShipmentRequest request, UUID actorUserId) {
        Shipment shipment = getShipmentByTrackingCodeOrThrow(request.trackingCode());
        User actor = userService.getUserEntityById(actorUserId);

        Hub intakeHub = resolveIntakeHub(actor, request.hubId(), shipment.getOriginHub());
        if (shipment.getOriginHub() != null && !shipment.getOriginHub().getId().equals(intakeHub.getId())) {
            throw new ForbiddenException("WRONG_ORIGIN_HUB", "Shipment must be received at its origin hub");
        }

        boolean sameHub = shipment.getDestHub() != null && intakeHub.getId().equals(shipment.getDestHub().getId());
        ShipmentStatus targetStatus = sameHub ? ShipmentStatus.AT_DEST_HUB : ShipmentStatus.AT_ORIGIN_HUB;

        // Idempotent replay: already received at this origin hub
        if (shipment.getCurrentStatus() == targetStatus
                && shipment.getCurrentHub() != null
                && shipment.getCurrentHub().getId().equals(intakeHub.getId())) {
            return ShipmentResponse.from(shipment);
        }

        if (shipment.getCurrentStatus() != ShipmentStatus.CREATED
                && shipment.getCurrentStatus() != ShipmentStatus.PICKED_UP) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), targetStatus);
        }

        shipment.updateCurrentHub(intakeHub);
        String note = hasText(request.note())
                ? request.note().trim()
                : (sameHub ? "Received at origin hub (same as destination hub)" : "Received at origin hub");
        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                targetStatus,
                actor,
                "HUB_INTAKE",
                note,
                intakeHub,
                null,
                null
        );
        return ShipmentResponse.from(updated);
    }

    @Transactional
    public ReweighShipmentResponse reweighAtHub(UUID shipmentId, ReweighShipmentRequest request, UUID actorUserId) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        boolean atOriginHub = shipment.getCurrentStatus() == ShipmentStatus.AT_ORIGIN_HUB;
        boolean sameHubAtDest = shipment.getCurrentStatus() == ShipmentStatus.AT_DEST_HUB
                && shipment.getOriginHub() != null
                && shipment.getCurrentHub() != null
                && shipment.getOriginHub().getId().equals(shipment.getCurrentHub().getId());
        if (!atOriginHub && !sameHubAtDest) {
            throw new InvalidShipmentStateTransitionException("Shipment can only be reweighed at origin hub");
        }

        User actor = userService.getUserEntityById(actorUserId);
        Hub activeHub = shipment.getCurrentHub() != null ? shipment.getCurrentHub() : shipment.getOriginHub();
        assertHubAccess(actor, activeHub);

        int oldChargeable = shipment.getChargeableWeightGram();
        BigDecimal oldShippingFee = shipment.getShippingFee();
        BigDecimal oldAmountToCollect = shipment.getAmountToCollect();

        int volumetric = pricingEngineService.calculateVolumetricWeight(
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                shipment.getServiceType().getVolumetricDivisor()
        );
        int newChargeable = pricingEngineService.determineChargeableWeight(request.actualWeightGram(), volumetric);
        BigDecimal newBaseFee = pricingEngineService.calculateBaseFee(shipment.getRateCard(), newChargeable);
        BigDecimal newShippingFee = pricingEngineService.calculateShippingFee(newBaseFee, shipment.getInsuranceFee());
        BigDecimal newReturnFee = pricingEngineService.calculateReturnFee(newBaseFee, shipment.getRateCard().getReturnFeeRatio());
        // ponytail: [NEEDS_CONFIRMATION] auto-updates amountToCollect when feePayer == RECEIVER and fee increases; add shop confirmation flow if business requires holding/charging shop instead
        BigDecimal newAmountToCollect = pricingEngineService.calculateAmountToCollect(
                shipment.getCodAmount(),
                newShippingFee,
                shipment.getFeePayer()
        );

        int weightDelta = newChargeable - oldChargeable;
        BigDecimal feeDelta = newShippingFee.subtract(oldShippingFee);

        shipment.applyReweigh(
                request.actualWeightGram(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm(),
                newChargeable,
                newBaseFee,
                newShippingFee,
                newReturnFee,
                newAmountToCollect
        );
        Shipment saved = shipmentRepository.save(shipment);

        boolean changed = weightDelta != 0 || feeDelta.signum() != 0;
        String baseNote = String.format(
                "Actual reweigh: %dg -> %dg (delta %+dg); shipping fee %s -> %s (delta %s)",
                oldChargeable, newChargeable, weightDelta, oldShippingFee, newShippingFee, feeDelta
        );
        String fullNote = hasText(request.note()) ? baseNote + " | " + request.note().trim() : baseNote;
        trackingEventRepository.save(TrackingEvent.builder()
                .shipment(saved)
                .status(saved.getCurrentStatus())
                .actor(actor)
                .hub(activeHub)
                .reasonCode(changed ? "REWEIGH_ADJUSTED" : "REWEIGH_CONFIRMED")
                .note(fullNote)
                .occurredAt(Instant.now())
                .build());

        return new ReweighShipmentResponse(
                ShipmentResponse.from(saved),
                oldChargeable,
                newChargeable,
                weightDelta,
                oldShippingFee,
                newShippingFee,
                feeDelta,
                oldAmountToCollect,
                newAmountToCollect
        );
    }

    @Transactional
    public ShipmentResponse handleShopAction(UUID shipmentId, ShopShipmentActionRequest request, UUID shopUserId) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        if (!shipment.getShop().getId().equals(shopUserId)) {
            throw new ForbiddenException("ACCESS_DENIED", "You do not own this shipment");
        }
        User shop = userService.getUserEntityById(shopUserId);
        String reason = hasText(request.reason()) ? request.reason().trim() : null;

        return switch (request.actionType()) {
            case REDELIVER -> requestShopRedelivery(shipment, shop, reason);
            case RETURN -> requestShopReturn(shipment, shop, reason);
            case ADJUST_COD -> adjustShopCod(shipment, shop, request.newCodAmount(), reason);
        };
    }

    private ShipmentResponse requestShopRedelivery(Shipment shipment, User shop, String reason) {
        ShipmentStatus status = shipment.getCurrentStatus();
        boolean validStatus = status == ShipmentStatus.DELIVERY_FAILED
                || (status == ShipmentStatus.AT_DEST_HUB && shipment.getAttemptCount() > 0);
        if (!validStatus || shipment.getAttemptCount() >= ShipmentStateMachine.MAX_DELIVERY_ATTEMPTS) {
            throw new InvalidShipmentStateTransitionException(
                    "Redelivery request requires failed delivery with attempts below " + ShipmentStateMachine.MAX_DELIVERY_ATTEMPTS);
        }

        String note = reason != null ? "Shop requested redelivery: " + reason : "Shop requested redelivery";
        trackingEventRepository.save(TrackingEvent.builder()
                .shipment(shipment)
                .status(status)
                .actor(shop)
                .hub(shipment.getCurrentHub())
                .reasonCode("SHOP_REDELIVERY_REQUESTED")
                .note(note)
                .occurredAt(Instant.now())
                .build());
        return ShipmentResponse.from(shipment);
    }

    private ShipmentResponse requestShopReturn(Shipment shipment, User shop, String reason) {
        if (!SHOP_RETURNABLE_STATUSES.contains(shipment.getCurrentStatus())) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.RETURN_INITIATED);
        }
        String note = reason != null ? "Shop requested return: " + reason : "Shop requested return";
        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.RETURN_INITIATED,
                shop,
                "SHOP_RETURN_REQUESTED",
                note,
                shipment.getCurrentHub(),
                null,
                null
        );
        return ShipmentResponse.from(updated);
    }

    private ShipmentResponse adjustShopCod(Shipment shipment, User shop, BigDecimal newCodAmount, String reason) {
        if (!COD_ADJUSTABLE_STATUSES.contains(shipment.getCurrentStatus())) {
            throw new InvalidShipmentStateTransitionException(
                    "COD adjustment is not allowed in status " + shipment.getCurrentStatus());
        }
        if (newCodAmount == null || newCodAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("INVALID_COD_AMOUNT", "New COD amount must be non-negative");
        }

        BigDecimal oldCod = shipment.getCodAmount();
        BigDecimal normalizedCod = newCodAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newAmountToCollect = pricingEngineService.calculateAmountToCollect(
                normalizedCod,
                shipment.getShippingFee(),
                shipment.getFeePayer()
        );
        shipment.updateCodAmount(normalizedCod, newAmountToCollect);
        Shipment saved = shipmentRepository.save(shipment);

        String baseNote = String.format("Shop adjusted COD: %s -> %s (amountToCollect: %s)",
                oldCod, normalizedCod, newAmountToCollect);
        String note = reason != null ? baseNote + " | " + reason : baseNote;
        trackingEventRepository.save(TrackingEvent.builder()
                .shipment(saved)
                .status(saved.getCurrentStatus())
                .actor(shop)
                .hub(saved.getCurrentHub())
                .reasonCode("COD_ADJUSTED")
                .note(note)
                .occurredAt(Instant.now())
                .build());
        return ShipmentResponse.from(saved);
    }

    @Transactional
    public ShipmentResponse initiateReturn(UUID shipmentId, String reason, UUID actorUserId) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        if (shipment.getCurrentStatus() != ShipmentStatus.ON_HOLD_FOR_RETURN) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.RETURN_INITIATED);
        }
        User actor = userService.getUserEntityById(actorUserId);
        Hub activeHub = shipment.getCurrentHub() != null ? shipment.getCurrentHub() : shipment.getDestHub();
        assertHubAccess(actor, activeHub);

        String note = hasText(reason) ? reason.trim() : "Return initiated";
        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.RETURN_INITIATED,
                actor,
                "RETURN_INITIATED",
                note,
                activeHub,
                null,
                null
        );
        return ShipmentResponse.from(updated);
    }

    @Transactional
    public ShipmentResponse advanceReturnTransit(UUID shipmentId, ReturnTransitRequest request, UUID actorUserId) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        if (shipment.getCurrentStatus() != ShipmentStatus.RETURN_INITIATED
                && shipment.getCurrentStatus() != ShipmentStatus.IN_RETURN_TRANSIT) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.IN_RETURN_TRANSIT);
        }

        User actor = userService.getUserEntityById(actorUserId);
        Hub targetHub = resolveReturnTransitHub(actor, request != null ? request.hubId() : null, shipment);
        assertHubAccess(actor, targetHub);

        if (targetHub != null) {
            shipment.updateCurrentHub(targetHub);
        }

        if (request != null && request.shipperId() != null) {
            User shipper = userService.getUserEntityById(request.shipperId());
            if (!shipper.hasRole(Role.ROLE_SHIPPER) || shipper.getStatus() != Status.ACTIVE) {
                throw new BadRequestException("INVALID_SHIPPER", "Assigned user must be an active shipper");
            }
            if (shipper.getStaffProfile() != null && targetHub != null
                    && !targetHub.getId().equals(shipper.getStaffProfile().getHubId())) {
                throw new BadRequestException("SHIPPER_HUB_MISMATCH", "Return shipper does not belong to active hub");
            }
            // ponytail: [NEEDS_CONFIRMATION] reuses assigned_delivery_shipper_id for return transit; add dedicated return shipper column via migration if separate history is needed
            shipment.assignDeliveryShipper(shipper);
        }

        String note = (request != null && hasText(request.note()))
                ? request.note().trim()
                : (targetHub != null ? "Return in transit at hub " + targetHub.getCode() : "Return in transit");
        Shipment updated = shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.IN_RETURN_TRANSIT,
                actor,
                "RETURN_TRANSIT",
                note,
                targetHub,
                null,
                null
        );
        return ShipmentResponse.from(updated);
    }

    @Transactional
    public Shipment completeReturnEntity(UUID shipmentId, CompleteReturnRequest request, UUID actorUserId) {
        Shipment shipment = getShipmentOrThrow(shipmentId);
        if (shipment.getCurrentStatus() != ShipmentStatus.IN_RETURN_TRANSIT) {
            throw new InvalidShipmentStateTransitionException(shipment.getCurrentStatus(), ShipmentStatus.RETURNED);
        }

        User actor = userService.getUserEntityById(actorUserId);
        if (actor.hasRole(Role.ROLE_SHIPPER) && !actor.hasRole(Role.ROLE_ADMIN)) {
            if (shipment.getAssignedDeliveryShipper() == null
                    || !shipment.getAssignedDeliveryShipper().getId().equals(actorUserId)) {
                throw new ForbiddenException("NOT_ASSIGNED_SHIPPER", "Shipment is not assigned to this shipper for return");
            }
        } else {
            assertHubAccess(actor, shipment.getOriginHub());
        }

        BigDecimal returnFee = pricingEngineService.calculateReturnFee(
                shipment.getBaseFee(),
                shipment.getRateCard().getReturnFeeRatio()
        );
        shipment.applyReturnFee(returnFee);
        if (shipment.getOriginHub() != null) {
            shipment.updateCurrentHub(shipment.getOriginHub());
        }

        String note = (request != null && hasText(request.note()))
                ? request.note().trim()
                : "Returned to shop (return fee: " + returnFee + ")";
        return shipmentStateMachine.validateAndTransition(
                shipment,
                ShipmentStatus.RETURNED,
                actor,
                "RETURNED",
                note,
                shipment.getOriginHub(),
                request != null ? request.lat() : null,
                request != null ? request.lng() : null
        );
    }

    private Hub resolveReturnTransitHub(User actor, UUID requestedHubId, Shipment shipment) {
        if (requestedHubId != null) {
            return hubOperationService.getHubOrThrow(requestedHubId);
        }
        if (actor.getStaffProfile() != null && actor.getStaffProfile().getHubId() != null) {
            return hubOperationService.getHubOrThrow(actor.getStaffProfile().getHubId());
        }
        return shipment.getCurrentHub() != null ? shipment.getCurrentHub() : shipment.getOriginHub();
    }

    private void assertHubAccess(User actor, Hub requiredHub) {
        if (actor.hasRole(Role.ROLE_ADMIN) || requiredHub == null) {
            return;
        }
        if (actor.getStaffProfile() != null && !requiredHub.getId().equals(actor.getStaffProfile().getHubId())) {
            throw new ForbiddenException("HUB_ACCESS_DENIED", "You do not have access to this hub");
        }
    }

    private Hub resolveIntakeHub(User actor, UUID requestedHubId, Hub originHub) {
        UUID staffHubId = actor.getStaffProfile() != null ? actor.getStaffProfile().getHubId() : null;
        if (staffHubId != null && !actor.hasRole(Role.ROLE_ADMIN)) {
            if (requestedHubId != null && !requestedHubId.equals(staffHubId)) {
                throw new ForbiddenException("HUB_ACCESS_DENIED", "Hub staff can only receive shipments at their assigned hub");
            }
            return hubOperationService.getHubOrThrow(staffHubId);
        }
        UUID targetHubId = requestedHubId != null ? requestedHubId
                : (staffHubId != null ? staffHubId : (originHub != null ? originHub.getId() : null));
        if (targetHubId == null) {
            throw new BadRequestException("MISSING_HUB", "Hub ID is required for intake");
        }
        return hubOperationService.getHubOrThrow(targetHubId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Hub resolveHubFromAddress(Address address) {
        if (address.getWard() != null && address.getWard().getAssignedHubId() != null) {
            return hubOperationService.getHubOrThrow(address.getWard().getAssignedHubId());
        }
        return null;
    }

    private Address resolveAddress(
            UUID addressId,
            CreateVerifiedAddressRequest request,
            UUID verifiedByUserId,
            String label
    ) {
        Address addr;
        if (addressId != null) {
            addr = addressService.getAddressOrThrow(addressId);
        } else if (request != null) {
            addr = addressService.createVerifiedAddressSnapshot(request, verifiedByUserId);
        } else {
            throw new BadRequestException("MISSING_ADDRESS", label + " address is required");
        }

        if (addr.getNormalizationStatus() != NormalizationStatus.VERIFIED) {
            throw new BadRequestException("ADDRESS_NOT_VERIFIED", label + " address must have status VERIFIED");
        }
        if (addr.getProvince() == null || addr.getWard() == null) {
            throw new BadRequestException("ADDRESS_INCOMPLETE", label + " address must specify province and ward");
        }
        return addr;
    }

    private String generateUniqueTrackingCode() {
        String datePrefix = DATE_PREFIX_FORMAT.format(LocalDate.now(VN_ZONE));
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder("LE").append(datePrefix);
            for (int j = 0; j < 6; j++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!shipmentRepository.existsByTrackingCode(code)) {
                return code;
            }
        }
        return "UTE" + datePrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
