-- V3__create_address_and_hub_schema.sql

-- 1. Province / City
CREATE TABLE provinces
(
    code   VARCHAR(10)  NOT NULL,
    name   VARCHAR(100) NOT NULL,
    region VARCHAR(20)  NOT NULL,

    CONSTRAINT pk_provinces PRIMARY KEY (code),
    CONSTRAINT ck_provinces_region CHECK (region IN ('NORTH', 'CENTRAL', 'SOUTH'))
);

-- 2. Wards / Communes
CREATE TABLE wards
(
    code            VARCHAR(10)    NOT NULL,
    province_code   VARCHAR(10)    NOT NULL,
    name            VARCHAR(100)   NOT NULL,
    assigned_hub_id UUID,
    center_lat      NUMERIC(10, 7),
    center_lng      NUMERIC(10, 7),

    CONSTRAINT pk_wards PRIMARY KEY (code),
    CONSTRAINT fk_wards_province_code FOREIGN KEY (province_code) REFERENCES provinces (code)
);

CREATE INDEX idx_wards_province_code ON wards (province_code);

-- 3. Old 3-tier to New 2-tier Administrative Boundary Mapping Table
CREATE TABLE administrative_mappings
(
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    legacy_province_name VARCHAR(100) NOT NULL,
    legacy_district_name VARCHAR(100),
    legacy_ward_name     VARCHAR(100),
    alias_keywords       VARCHAR(255),
    new_province_code    VARCHAR(10)  NOT NULL,
    new_ward_code        VARCHAR(10)  NOT NULL,
    assigned_hub_id      UUID,
    default_lat          NUMERIC(10, 7),
    default_lng          NUMERIC(10, 7),

    CONSTRAINT pk_administrative_mappings PRIMARY KEY (id),
    CONSTRAINT fk_mappings_new_province FOREIGN KEY (new_province_code) REFERENCES provinces (code),
    CONSTRAINT fk_mappings_new_ward FOREIGN KEY (new_ward_code) REFERENCES wards (code)
);

-- Create Trigram index for ultra-fast fuzzy matching using unaccent & pg_trgm
CREATE INDEX idx_mappings_trgm ON administrative_mappings USING gin (
    (legacy_ward_name || ' ' || COALESCE(legacy_district_name, '') || ' ' || legacy_province_name || ' ' || COALESCE(alias_keywords, '')) gin_trgm_ops
    );

-- 4. Standardized Addresses Table
CREATE TABLE addresses
(
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    raw_text             VARCHAR(500)  NOT NULL,
    street_detail        VARCHAR(255),
    legacy_district      VARCHAR(100),
    legacy_ward          VARCHAR(100),
    province_code        VARCHAR(10),
    ward_code            VARCHAR(10),
    lat                  NUMERIC(10, 7),
    lng                  NUMERIC(10, 7),
    match_score          NUMERIC(4, 3) NOT NULL DEFAULT 0.000,
    normalization_status VARCHAR(30)   NOT NULL DEFAULT 'MANUAL_REVIEW_QUEUE',
    verified_by          UUID,
    created_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_province FOREIGN KEY (province_code) REFERENCES provinces (code),
    CONSTRAINT fk_addresses_ward FOREIGN KEY (ward_code) REFERENCES wards (code),
    CONSTRAINT fk_addresses_verified_by FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT ck_addresses_status CHECK (normalization_status IN ('VERIFIED', 'SUGGESTED', 'MANUAL_REVIEW_QUEUE', 'UNRESOLVED'))
);

CREATE INDEX idx_addresses_status ON addresses (normalization_status);

-- 5. Hubs Table (Post Offices / Sorting Centers)
CREATE TABLE hubs
(
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    code           VARCHAR(30)   NOT NULL,
    name           VARCHAR(150)  NOT NULL,
    level          VARCHAR(30)   NOT NULL,
    parent_hub_id  UUID,
    address_id     UUID,
    lat            NUMERIC(10, 7),
    lng            NUMERIC(10, 7),
    created_at     TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_hubs PRIMARY KEY (id),
    CONSTRAINT uq_hubs_code UNIQUE (code),
    CONSTRAINT fk_hubs_parent FOREIGN KEY (parent_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_hubs_address FOREIGN KEY (address_id) REFERENCES addresses (id),
    CONSTRAINT ck_hubs_level CHECK (level IN ('REGIONAL_HUB', 'LOCAL_POST_OFFICE'))
);

-- Add foreign key between shop_profiles and addresses created in V2
ALTER TABLE shop_profiles
    ADD CONSTRAINT fk_shop_profiles_pickup_address_id
        FOREIGN KEY (pickup_address_id) REFERENCES addresses (id);

-- Add staff_profiles table linking User with Hub
CREATE TABLE staff_profiles
(
    user_id              UUID        NOT NULL,
    hub_id               UUID,
    employee_code        VARCHAR(20) NOT NULL,
    vehicle_plate        VARCHAR(20),
    max_capacity_gram    INTEGER     NOT NULL DEFAULT 50000,
    max_orders_per_shift INTEGER     NOT NULL DEFAULT 35,
    created_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_staff_profiles PRIMARY KEY (user_id),
    CONSTRAINT uq_staff_profiles_code UNIQUE (employee_code),
    CONSTRAINT fk_staff_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_profiles_hub FOREIGN KEY (hub_id) REFERENCES hubs (id)
);