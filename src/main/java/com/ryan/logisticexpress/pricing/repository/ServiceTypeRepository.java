package com.ryan.logisticexpress.pricing.repository;

import com.ryan.logisticexpress.pricing.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {
    Optional<ServiceType> findByCodeIgnoreCase(String code);
}