-- V5__create_manifest_and_dispatch_schema.sql

-- 1. Trips
CREATE TABLE trips
(
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    code          VARCHAR(30) NOT NULL,
    origin_hub_id UUID        NOT NULL,
    dest_hub_id   UUID        NOT NULL,
    vehicle_plate VARCHAR(20),
    driver_name   VARCHAR(150),
    status        VARCHAR(30) NOT NULL DEFAULT 'IN_TRANSIT',
    departed_at   TIMESTAMPTZ(0),
    arrived_at    TIMESTAMPTZ(0),

    CONSTRAINT pk_trips PRIMARY KEY (id),
    CONSTRAINT uq_trips_code UNIQUE (code),
    CONSTRAINT fk_trips_origin_hub_id
        FOREIGN KEY (origin_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_trips_dest_hub_id
        FOREIGN KEY (dest_hub_id) REFERENCES hubs (id),

    CONSTRAINT ck_trips_status
        CHECK (status IN ('PLANNED', 'IN_TRANSIT', 'ARRIVED', 'CANCELLED')),
    CONSTRAINT ck_trips_different_hubs
        CHECK (origin_hub_id <> dest_hub_id)
);

CREATE INDEX idx_trips_origin_dest_status
    ON trips (origin_hub_id, dest_hub_id, status);


-- 2. Manifests
CREATE TABLE manifests
(
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    code          VARCHAR(30) NOT NULL,
    trip_id       UUID,
    origin_hub_id UUID        NOT NULL,
    dest_hub_id   UUID        NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_by    UUID        NOT NULL,
    received_by   UUID,
    version       BIGINT      NOT NULL DEFAULT 0,
    sealed_at     TIMESTAMPTZ(0),
    received_at   TIMESTAMPTZ(0),

    CONSTRAINT pk_manifests PRIMARY KEY (id),
    CONSTRAINT uq_manifests_code UNIQUE (code),
    CONSTRAINT fk_manifests_trip_id
        FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_manifests_origin_hub_id
        FOREIGN KEY (origin_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_manifests_dest_hub_id
        FOREIGN KEY (dest_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_manifests_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_manifests_received_by
        FOREIGN KEY (received_by) REFERENCES users (id),

    CONSTRAINT ck_manifests_status
        CHECK (status IN ('OPEN', 'SEALED', 'IN_TRANSIT', 'RECEIVED', 'DISCREPANCY')),
    CONSTRAINT ck_manifests_different_hubs
        CHECK (origin_hub_id <> dest_hub_id),
    CONSTRAINT ck_manifests_version
        CHECK (version >= 0)
);

CREATE INDEX idx_manifests_trip_id
    ON manifests (trip_id);

CREATE INDEX idx_manifests_origin_status
    ON manifests (origin_hub_id, status);

CREATE INDEX idx_manifests_dest_status
    ON manifests (dest_hub_id, status);


-- 3. Manifest Items
CREATE TABLE manifest_items
(
    id          UUID    NOT NULL DEFAULT gen_random_uuid(),
    manifest_id UUID    NOT NULL,
    shipment_id UUID    NOT NULL,
    scan_out_at TIMESTAMPTZ(0),
    scan_in_at  TIMESTAMPTZ(0),
    is_missing  BOOLEAN NOT NULL DEFAULT FALSE,
    is_extra    BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_manifest_items PRIMARY KEY (id),
    CONSTRAINT uq_manifest_items_manifest_shipment UNIQUE (manifest_id, shipment_id),
    CONSTRAINT fk_manifest_items_manifest_id
        FOREIGN KEY (manifest_id) REFERENCES manifests (id),
    CONSTRAINT fk_manifest_items_shipment_id
        FOREIGN KEY (shipment_id) REFERENCES shipments (id),

    CONSTRAINT ck_manifest_items_extra_not_missing
        CHECK (NOT (is_extra AND is_missing))
);

CREATE INDEX idx_manifest_items_manifest_id
    ON manifest_items (manifest_id);

CREATE INDEX idx_manifest_items_shipment_id
    ON manifest_items (shipment_id);


-- 4. Optimization Batches
CREATE TABLE optimization_batches
(
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    hub_id                 UUID           NOT NULL,
    route_date             DATE           NOT NULL,
    solver_status          VARCHAR(30)    NOT NULL,
    baseline_distance_km   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    optimized_distance_km  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    assigned_orders        INTEGER        NOT NULL DEFAULT 0,
    unassigned_orders      INTEGER        NOT NULL DEFAULT 0,
    vehicles_used          INTEGER        NOT NULL DEFAULT 0,
    total_duration_minutes INTEGER        NOT NULL DEFAULT 0,
    time_window_violations INTEGER        NOT NULL DEFAULT 0,
    created_by             UUID           NOT NULL,
    created_at             TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_optimization_batches PRIMARY KEY (id),
    CONSTRAINT fk_optimization_batches_hub_id
        FOREIGN KEY (hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_optimization_batches_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),

    CONSTRAINT ck_optimization_batches_baseline_distance_km
        CHECK (baseline_distance_km >= 0),
    CONSTRAINT ck_optimization_batches_optimized_distance_km
        CHECK (optimized_distance_km >= 0),
    CONSTRAINT ck_optimization_batches_assigned_orders
        CHECK (assigned_orders >= 0),
    CONSTRAINT ck_optimization_batches_unassigned_orders
        CHECK (unassigned_orders >= 0),
    CONSTRAINT ck_optimization_batches_vehicles_used
        CHECK (vehicles_used >= 0),
    CONSTRAINT ck_optimization_batches_total_duration_minutes
        CHECK (total_duration_minutes >= 0),
    CONSTRAINT ck_optimization_batches_time_window_violations
        CHECK (time_window_violations >= 0)
);

CREATE INDEX idx_optimization_batches_hub_date
    ON optimization_batches (hub_id, route_date);


-- 5. Dispatch Routes
CREATE TABLE dispatch_routes
(
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    optimization_batch_id UUID,
    hub_id                UUID           NOT NULL,
    shipper_id            UUID           NOT NULL,
    route_date            DATE           NOT NULL,
    status                VARCHAR(30)    NOT NULL DEFAULT 'PLANNED',
    optimized_distance_km NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total_parcels         INTEGER        NOT NULL DEFAULT 0,
    total_weight_gram     INTEGER        NOT NULL DEFAULT 0,

    CONSTRAINT pk_dispatch_routes PRIMARY KEY (id),
    CONSTRAINT fk_dispatch_routes_optimization_batch_id
        FOREIGN KEY (optimization_batch_id) REFERENCES optimization_batches (id),
    CONSTRAINT fk_dispatch_routes_hub_id
        FOREIGN KEY (hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_dispatch_routes_shipper_id
        FOREIGN KEY (shipper_id) REFERENCES users (id),

    CONSTRAINT ck_dispatch_routes_status
        CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'HANDED_OVER')),
    CONSTRAINT ck_dispatch_routes_optimized_distance_km
        CHECK (optimized_distance_km >= 0),
    CONSTRAINT ck_dispatch_routes_total_parcels
        CHECK (total_parcels >= 0),
    CONSTRAINT ck_dispatch_routes_total_weight_gram
        CHECK (total_weight_gram >= 0)
);

CREATE INDEX idx_dispatch_routes_hub_date
    ON dispatch_routes (hub_id, route_date);

CREATE INDEX idx_dispatch_routes_shipper_date
    ON dispatch_routes (shipper_id, route_date);

CREATE INDEX idx_dispatch_routes_batch_id
    ON dispatch_routes (optimization_batch_id);


-- 6. Delivery Stops
CREATE TABLE delivery_stops
(
    id                     UUID        NOT NULL DEFAULT gen_random_uuid(),
    route_id               UUID        NOT NULL,
    shipment_id            UUID        NOT NULL,
    stop_sequence          INTEGER     NOT NULL,
    planned_eta_minutes    INTEGER,
    time_window_start_min  INTEGER,
    time_window_end_min    INTEGER,
    actual_arrival_at      TIMESTAMPTZ(0),
    pod_image_url          VARCHAR(500),
    pod_receiver_signature TEXT,
    failure_reason_code    VARCHAR(50),
    stop_status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    CONSTRAINT pk_delivery_stops PRIMARY KEY (id),
    CONSTRAINT fk_delivery_stops_route_id
        FOREIGN KEY (route_id) REFERENCES dispatch_routes (id),
    CONSTRAINT fk_delivery_stops_shipment_id
        FOREIGN KEY (shipment_id) REFERENCES shipments (id),

    CONSTRAINT ck_delivery_stops_stop_sequence
        CHECK (stop_sequence > 0),
    CONSTRAINT ck_delivery_stops_stop_status
        CHECK (stop_status IN ('PENDING', 'ARRIVED', 'DELIVERED', 'FAILED', 'REFUSED', 'SKIPPED'))
);

CREATE UNIQUE INDEX uq_delivery_stops_active_shipment
    ON delivery_stops (shipment_id) WHERE stop_status IN ('PENDING', 'ARRIVED');

CREATE INDEX idx_delivery_stops_route_seq
    ON delivery_stops (route_id, stop_sequence);

CREATE INDEX idx_delivery_stops_shipment_id
    ON delivery_stops (shipment_id);
