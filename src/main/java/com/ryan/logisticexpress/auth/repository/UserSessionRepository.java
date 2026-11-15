package com.ryan.logisticexpress.auth.repository;

import com.ryan.logisticexpress.auth.entity.UserSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @EntityGraph(attributePaths = {"user", "user.roles", "user.credential", "user.profile", "user.shopProfile"})
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findByFamilyId(UUID familyId);

    List<UserSession> findByUserId(UUID userId);

    @Modifying
    @Query("""
            UPDATE UserSession s
            SET s.revokedAt = :now,
                s.revokeReason = :reason
            WHERE s.refreshTokenHash = :hash
              AND s.revokedAt IS NULL
            """)
    void revokeByRefreshTokenHash(@Param("hash") String hash, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE UserSession s
            SET s.revokedAt = :now,
                s.revokeReason = :reason
            WHERE s.familyId = :familyId
              AND s.revokedAt IS NULL
            """)
    void revokeFamily(@Param("familyId") UUID familyId, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE UserSession s
            SET s.revokedAt = :now,
                s.revokeReason = :reason
            WHERE s.user.id = :userId
              AND s.revokedAt IS NULL
            """)
    void revokeAllForUser(@Param("userId") UUID userId, @Param("reason") String reason, @Param("now") Instant now);
}
