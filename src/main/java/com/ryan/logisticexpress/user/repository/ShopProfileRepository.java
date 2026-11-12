package com.ryan.logisticexpress.user.repository;

import com.ryan.logisticexpress.user.entity.ShopProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShopProfileRepository extends JpaRepository<ShopProfile, UUID> {
    Optional<ShopProfile> findByShopName(String shopName);
}
