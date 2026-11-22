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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id", nullable = false, updatable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 20, updatable = false)
    private ZoneType zoneType;

    @Column(name = "base_weight_gram", nullable = false, updatable = false)
    private Integer baseWeightGram;

    @Column(name = "base_price", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal basePrice;

    @Column(name = "step_weight_gram", nullable = false, updatable = false)
    private Integer stepWeightGram;

    @Column(name = "step_price", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal stepPrice;

    @Builder.Default
    @Column(name = "return_fee_ratio", nullable = false, precision = 4, scale = 2, updatable = false)
    private BigDecimal returnFeeRatio = new BigDecimal("0.50");

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom = Instant.now();

    public void deactivate() {
        this.isActive = false;
    }
}
