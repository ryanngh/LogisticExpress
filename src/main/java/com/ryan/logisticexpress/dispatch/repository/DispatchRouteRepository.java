package com.ryan.logisticexpress.dispatch.repository;

import com.ryan.logisticexpress.dispatch.entity.DispatchRoute;
import com.ryan.logisticexpress.dispatch.enums.RouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DispatchRouteRepository extends JpaRepository<DispatchRoute, UUID> {

    List<DispatchRoute> findByShipperIdAndRouteDateOrderByIdAsc(UUID shipperId, LocalDate routeDate);

    List<DispatchRoute> findByShipperIdAndHubIdAndRouteDateAndStatusIn(
            UUID shipperId,
            UUID hubId,
            LocalDate routeDate,
            Collection<RouteStatus> statuses
    );
}
