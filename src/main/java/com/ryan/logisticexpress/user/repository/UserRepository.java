package com.ryan.logisticexpress.user.repository;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import org.springframework.data.jpa.repository.EntityGraph;
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

    boolean existsByUsername(String username);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.profile.phone = :phone")
    boolean existsByPhone(@Param("phone") String phone);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findByRole(@Param("role") Role role);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r != com.ryan.logisticexpress.user.enums.Role.ROLE_SHOP")
    List<User> findAllStaff();

    @EntityGraph(attributePaths = {"roles", "credential", "profile", "shopProfile"})
    @Query("""
            SELECT u FROM User u
            LEFT JOIN u.profile p
            WHERE u.username = :lowerIdentifier
               OR u.email = :lowerIdentifier
               OR p.phone = :rawIdentifier
            """)
    Optional<User> findByIdentifierWithDetails(
            @Param("lowerIdentifier") String lowerIdentifier,
            @Param("rawIdentifier") String rawIdentifier
    );

    @EntityGraph(attributePaths = {"roles", "credential", "profile", "shopProfile"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithDetails(@Param("id") UUID id);
}
