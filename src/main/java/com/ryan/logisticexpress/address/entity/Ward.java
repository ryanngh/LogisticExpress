package com.ryan.logisticexpress.address.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wards")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Ward {

    @Id
    @Column(name = "code", length = 10, nullable = false)
    @EqualsAndHashCode.Include
    private String code;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code", nullable = false)
    private Province province;
    @Column(name = "name", length = 100, nullable = false)
    private String name;
    @Column(name = "assigned_hub_id")
    private UUID assignedHubId;
    @Column(name = "center_lat", precision = 10, scale = 7)
    private BigDecimal centerLat;
    @Column(name = "center_lng", precision = 10, scale = 7)
    private BigDecimal centerLng;
}
