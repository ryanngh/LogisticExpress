package com.ryan.logisticexpress.address.dto;

import com.ryan.logisticexpress.address.entity.Province;
import com.ryan.logisticexpress.address.enums.Region;

public record ProvinceResponse(
        String code,
        String name,
        Region region
) {
    public static ProvinceResponse from(Province province) {
        return new ProvinceResponse(
                province.getCode(),
                province.getName(),
                province.getRegion()
        );
    }
}
