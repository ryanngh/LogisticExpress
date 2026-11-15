package com.ryan.logisticexpress.auth.repository;

import com.ryan.logisticexpress.auth.entity.VerificationToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    @EntityGraph(attributePaths = {"user", "user.roles", "user.credential", "user.profile", "user.shopProfile"})
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findByUserIdAndPurposeAndConsumedAtIsNull(UUID userId, String purpose);

    @Modifying
    @Query("""
            UPDATE VerificationToken t
            SET t.consumedAt = :now
            WHERE t.user.id = :userId
              AND t.purpose = :purpose
              AND t.consumedAt IS NULL
            """)
    void consumeActiveTokens(@Param("userId") UUID userId, @Param("purpose") String purpose, @Param("now") Instant now);
}
