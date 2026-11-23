package com.ryan.logisticexpress.shipment.entity;

import com.ryan.logisticexpress.address.entity.Address;
import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.pricing.entity.RateCard;
import com.ryan.logisticexpress.pricing.entity.ServiceType;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "shipments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_shipments_tracking_code", columnNames = "tracking_code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "tracking_code", nullable = false, unique = true, length = 30, updatable = false)
    private String trackingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false, updatable = false)
    private User shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_card_id", nullable = false)
    private RateCard rateCard;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 30)
    @Builder.Default
    private ShipmentStatus currentStatus = ShipmentStatus.CREATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_hub_id")
    private Hub originHub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_hub_id")
    private Hub destHub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_hub_id")
    private Hub currentHub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_pickup_shipper_id")
    private User assignedPickupShipper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_delivery_shipper_id")
    private User assignedDeliveryShipper;

    @Column(name = "sender_name", nullable = false, length = 150)
    private String senderName;

    @Column(name = "sender_phone", nullable = false, length = 20)
    private String senderPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_address_id", nullable = false)
    private Address pickupAddress;

    @Column(name = "receiver_name", nullable = false, length = 150)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_address_id", nullable = false)
    private Address deliveryAddress;

    @Column(name = "actual_weight_gram", nullable = false)
    private Integer actualWeightGram;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "chargeable_weight_gram", nullable = false)
    private Integer chargeableWeightGram;

    @Builder.Default
    @Column(name = "declared_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal declaredValue = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "cod_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Column(name = "base_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee;

    @Builder.Default
    @Column(name = "insurance_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal insuranceFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "return_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal returnFee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payer", nullable = false, length = 20)
    private FeePayer feePayer;

    @Builder.Default
    @Column(name = "amount_to_collect", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountToCollect = BigDecimal.ZERO;

    @Column(name = "delivery_time_window_start")
    private Integer deliveryTimeWindowStart;

    @Column(name = "delivery_time_window_end")
    private Integer deliveryTimeWindowEnd;

    @Column(name = "scheduled_delivery_date")
    private LocalDate scheduledDeliveryDate;

    @Builder.Default
    @Column(name = "pickup_attempt_count", nullable = false)
    private Integer pickupAttemptCount = 0;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Builder.Default
    @Column(name = "rescue_count", nullable = false)
    private Integer rescueCount = 0;

    @Column(name = "sla_deadline")
    private Instant slaDeadline;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    // Controlled transition method used by ShipmentStateMachine
    public void transitionToStatus(ShipmentStatus newStatus) {
        this.currentStatus = newStatus;
        if (newStatus == ShipmentStatus.DELIVERED) {
            this.deliveredAt = Instant.now();
        }
    }

    public void incrementPickupAttemptCount() {
        this.pickupAttemptCount++;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public void incrementRescueCount() {
        this.rescueCount++;
    }

    public void updateEditableDetails(
            String receiverName,
            String receiverPhone,
            Address deliveryAddress,
            Hub destHub,
            Integer actualWeightGram,
            Integer lengthCm,
            Integer widthCm,
            Integer heightCm,
            Integer chargeableWeightGram,
            BigDecimal declaredValue,
            BigDecimal codAmount,
            RateCard rateCard,
            BigDecimal baseFee,
            BigDecimal shippingFee,
            BigDecimal insuranceFee,
            BigDecimal returnFee,
            FeePayer feePayer,
            BigDecimal amountToCollect
    ) {
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.deliveryAddress = deliveryAddress;
        this.destHub = destHub;
        this.actualWeightGram = actualWeightGram;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.chargeableWeightGram = chargeableWeightGram;
        this.declaredValue = declaredValue;
        this.codAmount = codAmount;
        this.rateCard = rateCard;
        this.baseFee = baseFee;
        this.shippingFee = shippingFee;
        this.insuranceFee = insuranceFee;
        this.returnFee = returnFee;
        this.feePayer = feePayer;
        this.amountToCollect = amountToCollect;
    }

    public void assignPickupShipper(User shipper) {
        this.assignedPickupShipper = shipper;
    }

    public void assignDeliveryShipper(User shipper) {
        this.assignedDeliveryShipper = shipper;
    }

    public void updateCurrentHub(Hub hub) {
        this.currentHub = hub;
    }

    public void applyReweigh(
            Integer actualWeightGram,
            Integer lengthCm,
            Integer widthCm,
            Integer heightCm,
            Integer chargeableWeightGram,
            BigDecimal baseFee,
            BigDecimal shippingFee,
            BigDecimal returnFee,
            BigDecimal amountToCollect
    ) {
        this.actualWeightGram = actualWeightGram;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.chargeableWeightGram = chargeableWeightGram;
        this.baseFee = baseFee;
        this.shippingFee = shippingFee;
        this.returnFee = returnFee;
        this.amountToCollect = amountToCollect;
    }
}
