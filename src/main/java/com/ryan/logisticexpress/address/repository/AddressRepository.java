package com.ryan.logisticexpress.address.repository;

import com.ryan.logisticexpress.address.entity.Address;
import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByNormalizationStatusOrderByCreatedAtDesc(NormalizationStatus status);
}