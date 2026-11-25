package com.ryan.logisticexpress.dispatch.entity;

import com.ryan.logisticexpress.dispatch.enums.StopStatus;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_stops")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DeliveryStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false, updatable = false)
    private DispatchRoute route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false, updatable = false)
    private Shipment shipment;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "planned_eta_minutes")
    private Integer plannedEtaMinutes;

    @Column(name = "time_window_start_min")
    private Integer timeWindowStartMin;

    @Column(name = "time_window_end_min")
    private Integer timeWindowEndMin;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    @Column(name = "pod_image_url", length = 500)
    private String podImageUrl;

    @Column(name = "pod_receiver_signature", columnDefinition = "TEXT")
    private String podReceiverSignature;

    @Column(name = "failure_reason_code", length = 50)
    private String failureReasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_status", nullable = false, length = 30)
    @Builder.Default
    private StopStatus stopStatus = StopStatus.PENDING;

    public void attachPodImage(String podImageUrl) {
        this.podImageUrl = podImageUrl;
    }

    public void markDelivered(Instant arrivalAt, String podImageUrl, String podReceiverSignature) {
        this.actualArrivalAt = arrivalAt;
        if (podImageUrl != null && !podImageUrl.isBlank()) {
            this.podImageUrl = podImageUrl.trim();
        }
        if (podReceiverSignature != null && !podReceiverSignature.isBlank()) {
            this.podReceiverSignature = podReceiverSignature.trim();
        }
        this.stopStatus = StopStatus.DELIVERED;
    }

    public void markFailed(Instant arrivalAt, String failureReasonCode) {
        this.actualArrivalAt = arrivalAt;
        this.failureReasonCode = failureReasonCode;
        this.stopStatus = StopStatus.FAILED;
    }

    public void markRefused(Instant arrivalAt, String failureReasonCode) {
        this.actualArrivalAt = arrivalAt;
        this.failureReasonCode = failureReasonCode;
        this.stopStatus = StopStatus.REFUSED;
    }

    public void markSkipped() {
        this.stopStatus = StopStatus.SKIPPED;
    }
}
