package com.ryan.logisticexpress.address.repository;

import com.ryan.logisticexpress.address.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WardRepository extends JpaRepository<Ward, String> {
    List<Ward> findByProvince_CodeOrderByCodeAsc(String provinceCode);
    List<Ward> findByAssignedHubIdOrderByCodeAsc(UUID assignedHubId);
}
