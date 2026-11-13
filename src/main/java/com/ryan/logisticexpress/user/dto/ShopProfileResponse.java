package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.user.entity.ShopProfile;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopProfileResponse(
        UUID userId,
        String shopName,
        UUID pickupAddressId,
        String bankName,
        String bankAccountNo,
        String bankAccountHolder,
        BigDecimal debtBalance
) {
    public static ShopProfileResponse from(ShopProfile entity) {
        if (entity == null) {
            return null;
        }
        return new ShopProfileResponse(
                entity.getUserId(),
                entity.getShopName(),
                entity.getPickupAddressId(),
                entity.getBankName(),
                entity.getBankAccountNo(),
                entity.getBankAccountHolder(),
                entity.getDebtBalance()
        );
    }
}
