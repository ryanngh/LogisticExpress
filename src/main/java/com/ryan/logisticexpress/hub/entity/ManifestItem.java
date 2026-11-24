package com.ryan.logisticexpress.hub.entity;

import com.ryan.logisticexpress.shipment.entity.Shipment;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "manifest_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_manifest_items_manifest_shipment", columnNames = {"manifest_id", "shipment_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ManifestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manifest_id", nullable = false, updatable = false)
    private Manifest manifest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false, updatable = false)
    private Shipment shipment;

    @Column(name = "scan_out_at")
    private Instant scanOutAt;

    @Column(name = "scan_in_at")
    private Instant scanInAt;

    @Builder.Default
    @Column(name = "is_missing", nullable = false)
    private boolean missing = false;

    @Builder.Default
    @Column(name = "is_extra", nullable = false)
    private boolean extra = false;

    public void ensureScanOutAt(Instant at) {
        if (this.scanOutAt == null) {
            this.scanOutAt = at;
        }
    }

    public void markScannedIn(Instant at) {
        this.scanInAt = at;
        this.missing = false;
    }

    public void markMissing() {
        this.missing = true;
    }
}
