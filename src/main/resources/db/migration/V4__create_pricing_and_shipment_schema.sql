-- V4__create_pricing_and_shipment_schema.sql

-- 1. Rate Cards
CREATE TABLE rate_cards
(
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    service_type_id  UUID           NOT NULL,
    zone_type        VARCHAR(20)    NOT NULL,
    base_weight_gram INTEGER        NOT NULL,
    base_price       NUMERIC(15, 2) NOT NULL,
    step_weight_gram INTEGER        NOT NULL,
    step_price       NUMERIC(15, 2) NOT NULL,
    return_fee_ratio NUMERIC(4, 2)  NOT NULL DEFAULT 0.50,
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE,
    effective_from   TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_rate_cards PRIMARY KEY (id),
    CONSTRAINT fk_rate_cards_service_type_id
        FOREIGN KEY (service_type_id) REFERENCES service_types (id),

    CONSTRAINT ck_rate_cards_zone_type
        CHECK (zone_type IN ('INTRA_PROVINCE', 'SAME_REGION', 'INTER_REGION')),
    CONSTRAINT ck_rate_cards_base_weight_gram
        CHECK (base_weight_gram > 0),
    CONSTRAINT ck_rate_cards_base_price
        CHECK (base_price >= 0),
    CONSTRAINT ck_rate_cards_step_weight_gram
        CHECK (step_weight_gram > 0),
    CONSTRAINT ck_rate_cards_step_price
        CHECK (step_price >= 0),
    CONSTRAINT ck_rate_cards_return_fee_ratio
        CHECK (return_fee_ratio >= 0)
);

CREATE UNIQUE INDEX uq_rate_cards_active
    ON rate_cards (service_type_id, zone_type) WHERE is_active = TRUE;

CREATE INDEX idx_rate_cards_service_zone
    ON rate_cards (service_type_id, zone_type);

-- Trigger: Rate cards cannot be modified except is_active
CREATE
OR REPLACE FUNCTION trg_rate_cards_check_update()
    RETURNS TRIGGER AS $$
BEGIN
    IF
(NEW.id IS DISTINCT FROM OLD.id
        OR NEW.service_type_id IS DISTINCT FROM OLD.service_type_id
        OR NEW.zone_type IS DISTINCT FROM OLD.zone_type
        OR NEW.base_weight_gram IS DISTINCT FROM OLD.base_weight_gram
        OR NEW.base_price IS DISTINCT FROM OLD.base_price
        OR NEW.step_weight_gram IS DISTINCT FROM OLD.step_weight_gram
        OR NEW.step_price IS DISTINCT FROM OLD.step_price
        OR NEW.return_fee_ratio IS DISTINCT FROM OLD.return_fee_ratio
        OR NEW.effective_from IS DISTINCT FROM OLD.effective_from) THEN
        RAISE EXCEPTION 'Cannot update immutable columns of rate_cards; only is_active can be modified';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_rate_cards_prevent_immutable_update
    BEFORE UPDATE
    ON rate_cards
    FOR EACH ROW EXECUTE FUNCTION trg_rate_cards_check_update();


-- 2. Shipments
CREATE TABLE shipments
(
    id                           UUID           NOT NULL DEFAULT gen_random_uuid(),
    tracking_code                VARCHAR(30)    NOT NULL,
    shop_id                      UUID           NOT NULL,
    service_type_id              UUID           NOT NULL,
    rate_card_id                 UUID           NOT NULL,
    current_status               VARCHAR(30)    NOT NULL DEFAULT 'CREATED',
    origin_hub_id                UUID,
    dest_hub_id                  UUID,
    current_hub_id               UUID,
    assigned_pickup_shipper_id   UUID,
    assigned_delivery_shipper_id UUID,
    sender_name                  VARCHAR(150)   NOT NULL,
    sender_phone                 VARCHAR(20)    NOT NULL,
    pickup_address_id            UUID           NOT NULL,
    receiver_name                VARCHAR(150)   NOT NULL,
    receiver_phone               VARCHAR(20)    NOT NULL,
    delivery_address_id          UUID           NOT NULL,
    actual_weight_gram           INTEGER        NOT NULL,
    length_cm                    INTEGER,
    width_cm                     INTEGER,
    height_cm                    INTEGER,
    chargeable_weight_gram       INTEGER        NOT NULL,
    declared_value               NUMERIC(15, 2) NOT NULL DEFAULT 0,
    cod_amount                   NUMERIC(15, 2) NOT NULL DEFAULT 0,
    base_fee                     NUMERIC(15, 2) NOT NULL,
    shipping_fee                 NUMERIC(15, 2) NOT NULL,
    insurance_fee                NUMERIC(15, 2) NOT NULL DEFAULT 0,
    return_fee                   NUMERIC(15, 2) NOT NULL DEFAULT 0,
    fee_payer                    VARCHAR(20)    NOT NULL,
    amount_to_collect            NUMERIC(15, 2) NOT NULL DEFAULT 0,
    delivery_time_window_start   INTEGER,
    delivery_time_window_end     INTEGER,
    scheduled_delivery_date      DATE,
    pickup_attempt_count         INTEGER        NOT NULL DEFAULT 0,
    attempt_count                INTEGER        NOT NULL DEFAULT 0,
    rescue_count                 INTEGER        NOT NULL DEFAULT 0,
    sla_deadline                 TIMESTAMPTZ(0),
    idempotency_key              VARCHAR(100),
    version                      BIGINT         NOT NULL DEFAULT 0,
    created_at                   TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    delivered_at                 TIMESTAMPTZ(0),

    CONSTRAINT pk_shipments PRIMARY KEY (id),
    CONSTRAINT uq_shipments_tracking_code UNIQUE (tracking_code),
    CONSTRAINT fk_shipments_shop_id
        FOREIGN KEY (shop_id) REFERENCES users (id),
    CONSTRAINT fk_shipments_service_type_id
        FOREIGN KEY (service_type_id) REFERENCES service_types (id),
    CONSTRAINT fk_shipments_rate_card_id
        FOREIGN KEY (rate_card_id) REFERENCES rate_cards (id),
    CONSTRAINT fk_shipments_origin_hub_id
        FOREIGN KEY (origin_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_shipments_dest_hub_id
        FOREIGN KEY (dest_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_shipments_current_hub_id
        FOREIGN KEY (current_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_shipments_pickup_shipper_id
        FOREIGN KEY (assigned_pickup_shipper_id) REFERENCES users (id),
    CONSTRAINT fk_shipments_delivery_shipper_id
        FOREIGN KEY (assigned_delivery_shipper_id) REFERENCES users (id),
    CONSTRAINT fk_shipments_pickup_address_id
        FOREIGN KEY (pickup_address_id) REFERENCES addresses (id),
    CONSTRAINT fk_shipments_delivery_address_id
        FOREIGN KEY (delivery_address_id) REFERENCES addresses (id),

    CONSTRAINT ck_shipments_fee_payer
        CHECK (fee_payer IN ('SENDER', 'RECEIVER')),
    CONSTRAINT ck_shipments_current_status
        CHECK (current_status IN (
                                  'CREATED', 'CANCELLED', 'PICKUP_ASSIGNED', 'PICKED_UP',
                                  'AT_ORIGIN_HUB', 'IN_LINEHAUL', 'AT_DEST_HUB', 'OUT_FOR_DELIVERY',
                                  'DELIVERED', 'DELIVERY_FAILED', 'REFUSED', 'ON_HOLD_FOR_RETURN',
                                  'RETURN_INITIATED', 'IN_RETURN_TRANSIT', 'RETURNED'
            )),
    CONSTRAINT ck_shipments_actual_weight_gram
        CHECK (actual_weight_gram > 0),
    CONSTRAINT ck_shipments_length_cm
        CHECK (length_cm IS NULL OR length_cm > 0),
    CONSTRAINT ck_shipments_width_cm
        CHECK (width_cm IS NULL OR width_cm > 0),
    CONSTRAINT ck_shipments_height_cm
        CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT ck_shipments_chargeable_weight_gram
        CHECK (chargeable_weight_gram > 0),
    CONSTRAINT ck_shipments_declared_value
        CHECK (declared_value >= 0),
    CONSTRAINT ck_shipments_cod_amount
        CHECK (cod_amount >= 0),
    CONSTRAINT ck_shipments_base_fee
        CHECK (base_fee >= 0),
    CONSTRAINT ck_shipments_shipping_fee
        CHECK (shipping_fee >= 0),
    CONSTRAINT ck_shipments_insurance_fee
        CHECK (insurance_fee >= 0),
    CONSTRAINT ck_shipments_return_fee
        CHECK (return_fee >= 0),
    CONSTRAINT ck_shipments_amount_to_collect
        CHECK (amount_to_collect >= 0),
    CONSTRAINT ck_shipments_pickup_attempt_count
        CHECK (pickup_attempt_count >= 0),
    CONSTRAINT ck_shipments_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_shipments_rescue_count
        CHECK (rescue_count >= 0)
);

CREATE UNIQUE INDEX uq_shipments_shop_idempotency
    ON shipments (shop_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_shipments_shop_status_created
    ON shipments (shop_id, current_status, created_at);

CREATE INDEX idx_shipments_current_hub_status
    ON shipments (current_hub_id, current_status);

CREATE INDEX idx_shipments_pickup_shipper_status
    ON shipments (assigned_pickup_shipper_id, current_status);

CREATE INDEX idx_shipments_delivery_shipper_status
    ON shipments (assigned_delivery_shipper_id, current_status);

CREATE INDEX idx_shipments_dest_hub_id
    ON shipments (dest_hub_id);


-- 3. Tracking Events (APPEND-ONLY)
CREATE TABLE tracking_events
(
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    shipment_id UUID        NOT NULL,
    status      VARCHAR(30) NOT NULL,
    hub_id      UUID,
    actor_id    UUID,
    reason_code VARCHAR(50),
    note        TEXT,
    lat         NUMERIC(9, 6),
    lng         NUMERIC(9, 6),
    occurred_at TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_tracking_events PRIMARY KEY (id),
    CONSTRAINT fk_tracking_events_shipment_id
        FOREIGN KEY (shipment_id) REFERENCES shipments (id),
    CONSTRAINT fk_tracking_events_hub_id
        FOREIGN KEY (hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_tracking_events_actor_id
        FOREIGN KEY (actor_id) REFERENCES users (id),

    CONSTRAINT ck_tracking_events_status
        CHECK (status IN (
                          'CREATED', 'CANCELLED', 'PICKUP_ASSIGNED', 'PICKED_UP',
                          'AT_ORIGIN_HUB', 'IN_LINEHAUL', 'AT_DEST_HUB', 'OUT_FOR_DELIVERY',
                          'DELIVERED', 'DELIVERY_FAILED', 'REFUSED', 'ON_HOLD_FOR_RETURN',
                          'RETURN_INITIATED', 'IN_RETURN_TRANSIT', 'RETURNED'
            ))
);

CREATE INDEX idx_tracking_events_shipment_occurred
    ON tracking_events (shipment_id, occurred_at);

-- Trigger: tracking_events is append-only
CREATE
OR REPLACE FUNCTION trg_tracking_events_prevent_mutation()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE
EXCEPTION 'tracking_events is append-only: updates and deletes are prohibited';
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_tracking_events_append_only
    BEFORE UPDATE OR
DELETE
ON tracking_events
    FOR EACH ROW EXECUTE FUNCTION trg_tracking_events_prevent_mutation();
