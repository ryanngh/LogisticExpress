package com.ryan.logisticexpress.pricing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "service_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_service_types_code", columnNames = "code")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "volumetric_divisor", nullable = false)
    private Integer volumetricDivisor;

    @Column(name = "sla_hours", nullable = false)
    private Integer slaHours;
}
