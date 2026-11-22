package com.ryan.logisticexpress.shipment.entity;

import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracking_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false, updatable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30, updatable = false)
    private ShipmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id", updatable = false)
    private Hub hub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @Column(name = "reason_code", length = 50, updatable = false)
    private String reasonCode;

    @Column(name = "note", columnDefinition = "TEXT", updatable = false)
    private String note;

    @Column(name = "lat", precision = 9, scale = 6, updatable = false)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6, updatable = false)
    private BigDecimal lng;

    @Builder.Default
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();
}
