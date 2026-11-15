package com.ryan.logisticexpress.address.repository;

import com.ryan.logisticexpress.address.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WardRepository extends JpaRepository<Ward, String> {
    List<Ward> findByProvinceCodeOrderByNameAsc(String provinceCode);
}