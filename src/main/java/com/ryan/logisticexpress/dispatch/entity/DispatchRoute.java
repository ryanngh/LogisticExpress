package com.ryan.logisticexpress.dispatch.entity;

import com.ryan.logisticexpress.dispatch.enums.RouteStatus;
import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dispatch_routes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DispatchRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    // ponytail: kept as plain UUID column until batch optimization entity is introduced
    @Column(name = "optimization_batch_id")
    private UUID optimizationBatchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id", nullable = false, updatable = false)
    private Hub hub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_id", nullable = false, updatable = false)
    private User shipper;

    @Column(name = "route_date", nullable = false, updatable = false)
    private LocalDate routeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RouteStatus status = RouteStatus.PLANNED;

    @Builder.Default
    @Column(name = "optimized_distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal optimizedDistanceKm = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_parcels", nullable = false)
    private Integer totalParcels = 0;

    @Builder.Default
    @Column(name = "total_weight_gram", nullable = false)
    private Integer totalWeightGram = 0;

    public void updateTotals(int totalParcels, int totalWeightGram) {
        this.totalParcels = totalParcels;
        this.totalWeightGram = totalWeightGram;
    }

    public void markInProgress() {
        this.status = RouteStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = RouteStatus.COMPLETED;
    }

    public void markHandedOver() {
        this.status = RouteStatus.HANDED_OVER;
    }
}
