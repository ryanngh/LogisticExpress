package com.ryan.logisticexpress.hub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "trips",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_trips_code", columnNames = "code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Trip {

    public enum Status {
        PLANNED,
        IN_TRANSIT,
        ARRIVED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_hub_id", nullable = false, updatable = false)
    private Hub originHub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_hub_id", nullable = false, updatable = false)
    private Hub destHub;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "driver_name", length = 150)
    private String driverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.IN_TRANSIT;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    public void markArrived(Instant arrivedAt) {
        this.status = Status.ARRIVED;
        this.arrivedAt = arrivedAt;
    }
}
