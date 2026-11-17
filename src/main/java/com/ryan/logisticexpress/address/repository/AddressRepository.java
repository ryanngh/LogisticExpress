package com.ryan.logisticexpress.address.repository;

import com.ryan.logisticexpress.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
}
