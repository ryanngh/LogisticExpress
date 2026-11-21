package com.ryan.logisticexpress.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateVerifiedAddressRequest(
        @NotBlank(message = "Street detail cannot be blank")
        @Size(max = 255, message = "Street detail must not exceed 255 characters")
        String streetDetail,

        @NotBlank(message = "Province code cannot be blank")
        @Size(max = 20, message = "Province code must not exceed 20 characters")
        String provinceCode,

        @NotBlank(message = "Ward code cannot be blank")
        @Size(max = 20, message = "Ward code must not exceed 20 characters")
        String wardCode,

        BigDecimal lat,
        BigDecimal lng
) {}
