package com.ryan.logisticexpress.pricing.entity;

import com.ryan.logisticexpress.pricing.enums.ZoneType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rate_cards")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", length = 30, nullable = false)
    private ZoneType zoneType;

    @Column(name = "base_weight_gram", nullable = false)
    private Integer baseWeightGram;

    @Column(name = "base_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal basePrice;

    @Column(name = "step_weight_gram", nullable = false)
    private Integer stepWeightGram;

    @Column(name = "step_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal stepPrice;

    @Column(name = "return_fee_ratio", precision = 4, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal returnFeeRatio = new BigDecimal("0.50");

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}