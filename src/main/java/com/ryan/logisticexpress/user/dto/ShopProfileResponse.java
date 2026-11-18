package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.user.entity.ShopProfile;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopProfileResponse(
        UUID userId,
        String shopName,
        UUID pickupAddressId,
        AddressResponse pickupAddress,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder,
        BigDecimal debtBalance
) {
    public ShopProfileResponse(
            UUID userId,
            String shopName,
            UUID pickupAddressId,
            String bankName,
            String bankAccountNo,
            String bankAccountHolder,
            BigDecimal debtBalance
    ) {
        this(userId, shopName, pickupAddressId, null, bankName, bankAccountNo, bankAccountHolder, debtBalance);
    }

    public static ShopProfileResponse from(ShopProfile entity) {
        return from(entity, null);
    }

    public static ShopProfileResponse from(ShopProfile entity, AddressResponse pickupAddress) {
        if (entity == null) {
            return null;
        }
        return new ShopProfileResponse(
                entity.getUserId(),
                entity.getShopName(),
                entity.getPickupAddressId(),
                pickupAddress,
                entity.getBankName(),
                entity.getBankAccountNo(),
                entity.getBankAccountHolder(),
                entity.getDebtBalance()
        );
    }
}
