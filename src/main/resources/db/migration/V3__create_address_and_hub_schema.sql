-- V3__create_address_and_hub_schema.sql

-- 1. Provinces
CREATE TABLE provinces
(
    code   VARCHAR(20)  NOT NULL,
    name   VARCHAR(100) NOT NULL,
    region VARCHAR(20)  NOT NULL,

    CONSTRAINT pk_provinces PRIMARY KEY (code),
    CONSTRAINT ck_provinces_region
        CHECK (region IN ('SOUTH', 'CENTRAL', 'NORTH'))
);


-- 2. Wards
CREATE TABLE wards
(
    code            VARCHAR(20)  NOT NULL,
    province_code   VARCHAR(20)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    assigned_hub_id UUID,
    center_lat      NUMERIC(9, 6),
    center_lng      NUMERIC(9, 6),

    CONSTRAINT pk_wards PRIMARY KEY (code),
    CONSTRAINT fk_wards_province_code
        FOREIGN KEY (province_code) REFERENCES provinces (code)
);

CREATE INDEX idx_wards_province_code ON wards (province_code);
CREATE INDEX idx_wards_assigned_hub_id ON wards (assigned_hub_id);


-- 3. Administrative Mappings
CREATE
OR REPLACE FUNCTION immutable_unaccent(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS
$$
SELECT public.unaccent('public.unaccent'::regdictionary, $1);
$$;

CREATE TABLE administrative_mappings
(
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    legacy_province_name VARCHAR(100) NOT NULL,
    legacy_district_name VARCHAR(100) NOT NULL,
    legacy_ward_name     VARCHAR(120) NOT NULL,
    alias_keywords       TEXT,
    new_province_code    VARCHAR(20)  NOT NULL,
    new_ward_code        VARCHAR(20)  NOT NULL,

    CONSTRAINT pk_administrative_mappings PRIMARY KEY (id),
    CONSTRAINT fk_administrative_mappings_new_province_code
        FOREIGN KEY (new_province_code) REFERENCES provinces (code),
    CONSTRAINT fk_administrative_mappings_new_ward_code
        FOREIGN KEY (new_ward_code) REFERENCES wards (code)
);

CREATE INDEX idx_admin_mappings_trgm
    ON administrative_mappings
    USING GIN (
    immutable_unaccent(
    legacy_province_name || ' ' || legacy_district_name || ' ' || legacy_ward_name || ' ' ||
    COALESCE (alias_keywords, '')
    ) gin_trgm_ops
    );

CREATE INDEX idx_admin_mappings_new_codes
    ON administrative_mappings (new_province_code, new_ward_code);


-- 4. Addresses (Record snapshot địa chỉ đã chuẩn hóa)
CREATE TABLE addresses
(
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    raw_text             VARCHAR(500) NOT NULL,
    street_detail        VARCHAR(255),
    legacy_district      VARCHAR(100),
    legacy_ward          VARCHAR(120),
    province_code        VARCHAR(20),
    ward_code            VARCHAR(20),
    lat                  NUMERIC(9, 6),
    lng                  NUMERIC(9, 6),
    match_score          NUMERIC(4, 3),
    normalization_status VARCHAR(30)  NOT NULL DEFAULT 'UNRESOLVED',
    verified_by          UUID,
    created_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_province_code
        FOREIGN KEY (province_code) REFERENCES provinces (code),
    CONSTRAINT fk_addresses_ward_code
        FOREIGN KEY (ward_code) REFERENCES wards (code),
    CONSTRAINT fk_addresses_verified_by
        FOREIGN KEY (verified_by) REFERENCES users (id),

    CONSTRAINT ck_addresses_match_score
        CHECK (match_score IS NULL OR (match_score >= 0 AND match_score <= 1)),
    CONSTRAINT ck_addresses_normalization_status
        CHECK (normalization_status IN ('VERIFIED', 'SUGGESTED', 'MANUAL_REVIEW_QUEUE', 'UNRESOLVED'))
);

CREATE INDEX idx_addresses_normalization_status ON addresses (normalization_status);
CREATE INDEX idx_addresses_province_ward ON addresses (province_code, ward_code);


-- 5. Hubs
CREATE TABLE hubs
(
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    code          VARCHAR(30)   NOT NULL,
    name          VARCHAR(150)  NOT NULL,
    level         VARCHAR(30)   NOT NULL,
    parent_hub_id UUID,
    address_id    UUID,
    lat           NUMERIC(9, 6) NOT NULL,
    lng           NUMERIC(9, 6) NOT NULL,
    created_at    TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_hubs PRIMARY KEY (id),
    CONSTRAINT uq_hubs_code UNIQUE (code),
    CONSTRAINT fk_hubs_parent_hub_id
        FOREIGN KEY (parent_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_hubs_address_id
        FOREIGN KEY (address_id) REFERENCES addresses (id),

    CONSTRAINT ck_hubs_level
        CHECK (level IN ('REGIONAL_HUB', 'LOCAL_POST_OFFICE'))
);

CREATE INDEX idx_hubs_parent_hub_id ON hubs (parent_hub_id);


-- 6. Service Types
CREATE TABLE service_types
(
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    code               VARCHAR(30)  NOT NULL,
    name               VARCHAR(100) NOT NULL,
    volumetric_divisor INTEGER      NOT NULL,
    sla_hours          INTEGER      NOT NULL,

    CONSTRAINT pk_service_types PRIMARY KEY (id),
    CONSTRAINT uq_service_types_code UNIQUE (code),

    CONSTRAINT ck_service_types_code
        CHECK (code IN ('STANDARD', 'EXPRESS')),
    CONSTRAINT ck_service_types_volumetric_divisor
        CHECK (volumetric_divisor > 0),
    CONSTRAINT ck_service_types_sla_hours
        CHECK (sla_hours > 0)
);


-- 7.
ALTER TABLE wards
    ADD CONSTRAINT fk_wards_assigned_hub_id
        FOREIGN KEY (assigned_hub_id) REFERENCES hubs (id);

ALTER TABLE shop_profiles
    ADD CONSTRAINT fk_shop_profiles_pickup_address_id
        FOREIGN KEY (pickup_address_id) REFERENCES addresses (id);


-- 8. Staff Profiles (1-1 users)
CREATE TABLE staff_profiles
(
    user_id              UUID        NOT NULL,
    hub_id               UUID        NOT NULL,
    employee_code        VARCHAR(20) NOT NULL,
    vehicle_plate        VARCHAR(20),
    max_capacity_gram    INTEGER     NOT NULL DEFAULT 50000,
    max_orders_per_shift INTEGER     NOT NULL DEFAULT 35,
    created_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_staff_profiles PRIMARY KEY (user_id),
    CONSTRAINT uq_staff_profiles_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_staff_profiles_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_profiles_hub_id
        FOREIGN KEY (hub_id) REFERENCES hubs (id),

    CONSTRAINT ck_staff_profiles_max_capacity_gram
        CHECK (max_capacity_gram > 0),
    CONSTRAINT ck_staff_profiles_max_orders_per_shift
        CHECK (max_orders_per_shift > 0)
);

CREATE INDEX idx_staff_profiles_hub_id ON staff_profiles (hub_id);
