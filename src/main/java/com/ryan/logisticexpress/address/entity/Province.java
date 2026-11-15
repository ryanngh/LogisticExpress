package com.ryan.logisticexpress.address.entity;

import com.ryan.logisticexpress.address.enums.Region;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "provinces")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)

public class Province {
    @Id
    @Column(name = "code", length = 10, nullable = false)
    @EqualsAndHashCode.Include
    private String code;
    @Column(name = "name", length = 100, nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "region", length = 20, nullable = false)
    private Region region;
}
