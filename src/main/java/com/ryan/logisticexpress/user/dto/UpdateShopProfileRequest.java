package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.address.dto.CreateVerifiedAddressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateShopProfileRequest(
        @NotBlank(message = "Shop name cannot be blank")
        @Size(max = 150, message = "Shop name must not exceed 150 characters")
        String shopName,

        UUID pickupAddressId,

        @Valid
        CreateVerifiedAddressRequest pickupAddress,

        @Size(max = 100, message = "Bank name must not exceed 100 characters")
        String bankName,

        @Size(max = 30, message = "Bank account number must not exceed 30 characters")
        String bankAccountNo,

        @Size(max = 150, message = "Bank account holder must not exceed 150 characters")
        String bankAccountHolder
) {
    public UpdateShopProfileRequest(
            String shopName,
            UUID pickupAddressId,
            String bankName,
            String bankAccountNo,
            String bankAccountHolder
    ) {
        this(shopName, pickupAddressId, null, bankName, bankAccountNo, bankAccountHolder);
    }
}
