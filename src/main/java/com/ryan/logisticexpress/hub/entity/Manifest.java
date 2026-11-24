package com.ryan.logisticexpress.hub.entity;

import com.ryan.logisticexpress.hub.enums.ManifestStatus;
import com.ryan.logisticexpress.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "manifests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_manifests_code", columnNames = "code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Manifest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_hub_id", nullable = false, updatable = false)
    private Hub originHub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_hub_id", nullable = false, updatable = false)
    private Hub destHub;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ManifestStatus status = ManifestStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "sealed_at")
    private Instant sealedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    public void seal(Instant sealedAt) {
        this.status = ManifestStatus.SEALED;
        this.sealedAt = sealedAt;
    }

    public void dispatchOnTrip(Trip trip) {
        this.trip = trip;
        this.status = ManifestStatus.IN_TRANSIT;
    }

    public void markReceived(User receivedBy, boolean hasDiscrepancy, Instant receivedAt) {
        this.receivedBy = receivedBy;
        this.receivedAt = receivedAt;
        this.status = hasDiscrepancy ? ManifestStatus.DISCREPANCY : ManifestStatus.RECEIVED;
    }
}
