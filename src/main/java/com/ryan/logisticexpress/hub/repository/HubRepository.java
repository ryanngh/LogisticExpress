package com.ryan.logisticexpress.hub.repository;

import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.hub.enums.HubLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRepository extends JpaRepository<Hub, UUID> {
    Optional<Hub> findByCode(String code);
    boolean existsByCode(String code);
    List<Hub> findByLevelOrderByCodeAsc(HubLevel level);
    List<Hub> findAllByOrderByCodeAsc();
    boolean existsByParentHub_Id(UUID parentHubId);
}
