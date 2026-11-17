-- V4__create_pricing_schema.sql

-- 1. Express
CREATE TABLE service_types
(
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    code               VARCHAR(30)  NOT NULL,
    name               VARCHAR(100) NOT NULL,
    volumetric_divisor INTEGER      NOT NULL DEFAULT 6000,
    sla_hours          INTEGER      NOT NULL DEFAULT 48,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_service_types PRIMARY KEY (id),
    CONSTRAINT uq_service_types_code UNIQUE (code),
    CONSTRAINT ck_service_types_divisor CHECK (volumetric_divisor IN (5000, 6000))
);

-- 2.RateCard
CREATE TABLE rate_cards
(
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    service_type_id   UUID           NOT NULL,
    zone_type         VARCHAR(30)    NOT NULL,
    base_weight_gram  INTEGER        NOT NULL DEFAULT 2000,
    base_price        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    step_weight_gram  INTEGER        NOT NULL DEFAULT 500,
    step_price        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    return_fee_ratio  NUMERIC(4, 2)  NOT NULL DEFAULT 0.50,
    created_at        TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_rate_cards PRIMARY KEY (id),
    CONSTRAINT fk_rate_cards_service_type FOREIGN KEY (service_type_id) REFERENCES service_types (id) ON DELETE CASCADE,
    CONSTRAINT uq_rate_cards_service_zone UNIQUE (service_type_id, zone_type),
    CONSTRAINT ck_rate_cards_zone_type CHECK (zone_type IN ('INTRA_PROVINCE', 'SAME_REGION', 'INTER_REGION')),
    CONSTRAINT ck_rate_cards_base_price CHECK (base_price >= 0),
    CONSTRAINT ck_rate_cards_step_price CHECK (step_price >= 0),
    CONSTRAINT ck_rate_cards_return_ratio CHECK (return_fee_ratio >= 0 AND return_fee_ratio <= 1.0)
);

-- STANDARD and EXPRESS
INSERT INTO service_types (id, code, name, volumetric_divisor, sla_hours) VALUES
('a0000000-0000-0000-0000-000000000001', 'STANDARD', 'Standard delivery', 6000, 48),
('a0000000-0000-0000-0000-000000000002', 'EXPRESS', 'Express delivery', 5000, 24);

-- Standard price list for the STANDARD service
INSERT INTO rate_cards (service_type_id, zone_type, base_weight_gram, base_price, step_weight_gram, step_price, return_fee_ratio) VALUES
('a0000000-0000-0000-0000-000000000001', 'INTRA_PROVINCE', 2000, 16000.00, 500, 2500.00, 0.50),
('a0000000-0000-0000-0000-000000000001', 'SAME_REGION',    2000, 22000.00, 500, 3000.00, 0.50),
('a0000000-0000-0000-0000-000000000001', 'INTER_REGION',   2000, 30000.00, 500, 5000.00, 0.50);