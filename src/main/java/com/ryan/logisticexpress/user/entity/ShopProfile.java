package com.ryan.logisticexpress.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop_profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ShopProfile {

    @Id
    @Column(name = "user_id")
    @EqualsAndHashCode.Include
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "shop_name", nullable = false, length = 150)
    private String shopName;

    @Column(name = "pickup_address_id")
    private UUID pickupAddressId;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_no", length = 30)
    private String bankAccountNo;

    @Column(name = "bank_account_holder", length = 150)
    private String bankAccountHolder;

    @Column(name = "debt_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal debtBalance = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.debtBalance == null) {
            this.debtBalance = BigDecimal.ZERO;
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
