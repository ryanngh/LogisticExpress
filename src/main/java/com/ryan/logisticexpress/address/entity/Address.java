package com.ryan.logisticexpress.address.entity;

import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @Column(name = "raw_text", length = 500, nullable = false)
    private String rawText;
    @Column(name = "street_detail", length = 255)
    private String streetDetail;
    @Column(name = "legacy_district", length = 100)
    private String legacyDistrict;
    @Column(name = "legacy_ward", length = 100)
    private String legacyWard;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code")
    private Province province;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_code")
    private Ward ward;
    @Column(name = "lat", precision = 10, scale = 7)
    private BigDecimal lat;
    @Column(name = "lng", precision = 10, scale = 7)
    private BigDecimal lng;
    @Column(name = "match_score", precision = 4, scale = 3, nullable = false)
    @Builder.Default
    private BigDecimal matchScore = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "normalization_status", length = 30, nullable = false)
    @Builder.Default
    private NormalizationStatus normalizationStatus = NormalizationStatus.MANUAL_REVIEW_QUEUE;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
