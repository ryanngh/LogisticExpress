package com.ryan.logisticexpress.user.repository;

import com.ryan.logisticexpress.user.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
    Optional<StaffProfile> findByEmployeeCode(String employeeCode);
    boolean existsByEmployeeCode(String employeeCode);
    List<StaffProfile> findByHubId(UUID hubId);
}
