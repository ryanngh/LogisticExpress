package com.ryan.logisticexpress.address.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateAddressRequest(
        @NotBlank(message = "Original address cannot be blank")
        String rawText,
        String streetDetail,
        String provinceCode,
        String wardCode,
        BigDecimal lat,
        BigDecimal lng
) {}
