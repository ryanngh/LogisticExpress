package com.ryan.logisticexpress.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_credentials")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserCredential {

    @Id
    @Column(name = "user_id")
    @EqualsAndHashCode.Include
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    @Builder.Default
    private Instant passwordChangedAt = Instant.now();

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private Boolean mustChangePassword = false;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private Integer failedLoginCount = 0;

    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @PrePersist
    protected void onCreate() {
        if (this.passwordChangedAt == null) {
            this.passwordChangedAt = Instant.now();
        }
        if (this.mustChangePassword == null) {
            this.mustChangePassword = false;
        }
        if (this.failedLoginCount == null) {
            this.failedLoginCount = 0;
        }
    }

    public boolean isAccountLocked() {
        return this.lockedUntil != null && this.lockedUntil.isAfter(Instant.now());
    }

    public void recordFailedLogin(int maxFailedAttempts, long lockDurationSeconds) {
        this.failedLoginCount = (this.failedLoginCount == null ? 0 : this.failedLoginCount) + 1;
        this.lastFailedLoginAt = Instant.now();
        if (this.failedLoginCount >= maxFailedAttempts) {
            this.lockedUntil = Instant.now().plusSeconds(lockDurationSeconds);
        }
    }

    public void resetFailedLogin() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }
}
