package com.ryan.logisticexpress.address.dto;

import jakarta.validation.constraints.NotBlank;

public record NormalizeAddressRequest(
        @NotBlank(message = "Address cannot be blank")
        String rawAddress
) {}
