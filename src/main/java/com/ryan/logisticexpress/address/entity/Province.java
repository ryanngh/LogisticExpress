package com.ryan.logisticexpress.address.entity;

import com.ryan.logisticexpress.address.enums.Region;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "provinces")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Province {

    @Id
    @Column(name = "code", nullable = false, length = 20)
    @EqualsAndHashCode.Include
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 20)
    private Region region;
}
