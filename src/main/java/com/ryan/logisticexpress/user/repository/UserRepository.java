package com.ryan.logisticexpress.user.repository;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.profile.phone = :phone")
    Optional<User> findByPhone(@Param("phone") String phone);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.profile.phone = :phone")
    boolean existsByPhone(@Param("phone") String phone);
}
