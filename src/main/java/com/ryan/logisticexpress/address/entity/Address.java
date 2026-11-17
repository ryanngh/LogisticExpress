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
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "raw_text", nullable = false, length = 500)
    private String rawText;

    @Column(name = "street_detail", length = 255)
    private String streetDetail;

    @Column(name = "legacy_district", length = 100)
    private String legacyDistrict;

    @Column(name = "legacy_ward", length = 120)
    private String legacyWard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code")
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_code")
    private Ward ward;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "match_score", precision = 4, scale = 3)
    private BigDecimal matchScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "normalization_status", nullable = false, length = 30)
    @Builder.Default
    private NormalizationStatus normalizationStatus = NormalizationStatus.UNRESOLVED;

    @Column(name = "verified_by")
    private UUID verifiedBy;

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
        if (this.normalizationStatus == null) {
            this.normalizationStatus = NormalizationStatus.UNRESOLVED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
