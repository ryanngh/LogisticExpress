package com.ryan.logisticexpress.address.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "administrative_mappings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AdministrativeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;
    @Column(name = "legacy_province_name", length = 100, nullable = false)
    private String legacyProvinceName;
    @Column(name = "legacy_district_name", length = 100)
    private String legacyDistrictName;
    @Column(name = "legacy_ward_name", length = 100)
    private String legacyWardName;
    @Column(name = "alias_keywords", length = 255)
    private String aliasKeywords;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_province_code", nullable = false)
    private Province newProvince;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_ward_code", nullable = false)
    private Ward newWard;
    @Column(name = "assigned_hub_id")
    private UUID assignedHubId;
    @Column(name = "default_lat", precision = 10, scale = 7)
    private BigDecimal defaultLat;
    @Column(name = "default_lng", precision = 10, scale = 7)
    private BigDecimal defaultLng;
}
