package com.ryan.logisticexpress.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "staff_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_staff_profiles_employee_code", columnNames = "employee_code")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StaffProfile {

    @Id
    @Column(name = "user_id")
    @EqualsAndHashCode.Include
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "hub_id", nullable = false)
    private UUID hubId;

    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "max_capacity_gram", nullable = false)
    @Builder.Default
    private Integer maxCapacityGram = 50000;

    @Column(name = "max_orders_per_shift", nullable = false)
    @Builder.Default
    private Integer maxOrdersPerShift = 35;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.maxCapacityGram == null) {
            this.maxCapacityGram = 50000;
        }
        if (this.maxOrdersPerShift == null) {
            this.maxOrdersPerShift = 35;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
