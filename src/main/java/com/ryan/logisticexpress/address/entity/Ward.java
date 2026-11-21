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
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Ward {

    @Id
    @Column(name = "code", nullable = false, length = 20)
    @EqualsAndHashCode.Include
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code", nullable = false)
    private Province province;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "assigned_hub_id")
    private UUID assignedHubId;

    @Column(name = "center_lat", precision = 9, scale = 6)
    private BigDecimal centerLat;

    @Column(name = "center_lng", precision = 9, scale = 6)
    private BigDecimal centerLng;
}
