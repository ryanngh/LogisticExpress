-- V2__create_users.sql

-- 1. Users
CREATE TABLE users
(
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    username          VARCHAR(50)  NOT NULL,
    email             VARCHAR(255) NOT NULL,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at TIMESTAMPTZ(0),
    token_version     INTEGER      NOT NULL DEFAULT 0,
    last_login_at     TIMESTAMPTZ(0),
    created_at        TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),

    CONSTRAINT ck_users_username
        CHECK (username ~ '^[a-z0-9_.]{3,50}$'
) ,

    CONSTRAINT ck_users_email
        CHECK (
            email = LOWER(BTRIM(email))
            AND email ~ '^[^@[:space:]]+@[^@[:space:]]+$'
        ),

    CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')),

    CONSTRAINT ck_users_token_version
        CHECK (token_version >= 0)
);


-- 2. User Credentials
CREATE TABLE user_credentials
(
    user_id              UUID         NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    password_changed_at  TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count   INTEGER      NOT NULL DEFAULT 0,
    last_failed_login_at TIMESTAMPTZ(0),
    locked_until         TIMESTAMPTZ(0),

    CONSTRAINT pk_user_credentials PRIMARY KEY (user_id),
    CONSTRAINT fk_user_credentials_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_user_credentials_failed_login_count
        CHECK (failed_login_count >= 0)
);


-- 3. User Profile
CREATE TABLE user_profile
(
    user_id    UUID         NOT NULL,
    full_name  VARCHAR(150) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    avatar_url VARCHAR(500),
    created_at TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_profile PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profile_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_user_profile_phone
        CHECK (phone ~ '^(0|\+84)[0-9]{9,10}$'
)
    );

CREATE INDEX idx_user_profile_phone ON user_profile (phone);


-- 4. User App Roles
CREATE TABLE user_app_roles
(
    user_id     UUID        NOT NULL,
    role        VARCHAR(30) NOT NULL,
    assigned_at TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_app_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_app_roles_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_user_app_roles_role
        CHECK (role IN (
                        'ROLE_ADMIN',
                        'ROLE_MANAGER',
                        'ROLE_DISPATCHER',
                        'ROLE_HUB_STAFF',
                        'ROLE_SHIPPER',
                        'ROLE_ACCOUNTANT',
                        'ROLE_CSKH',
                        'ROLE_SHOP'
            ))
);

CREATE INDEX idx_user_app_roles_role ON user_app_roles (role);


-- 5. Shop Profiles
CREATE TABLE shop_profiles
(
    user_id             UUID           NOT NULL,
    shop_name           VARCHAR(150)   NOT NULL,
    pickup_address_id   UUID,
    bank_name           VARCHAR(100),
    bank_account_no     VARCHAR(30),
    bank_account_holder VARCHAR(150),
    debt_balance        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_shop_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_shop_profiles_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_shop_profiles_debt_balance
        CHECK (debt_balance >= 0),

    CONSTRAINT ck_shop_profiles_version
        CHECK (version >= 0),

    CONSTRAINT ck_shop_profiles_bank_account_no
        CHECK (
            bank_account_no IS NULL
                OR bank_account_no ~ '^[0-9]{6,20}$'
) ,

    CONSTRAINT ck_shop_profiles_bank_complete
        CHECK (
            (
                bank_name IS NULL
                AND bank_account_no IS NULL
                AND bank_account_holder IS NULL
            )
            OR (
                NULLIF(BTRIM(bank_name), '') IS NOT NULL
                AND bank_account_no IS NOT NULL
                AND NULLIF(BTRIM(bank_account_holder), '') IS NOT NULL
            )
        )
);


-- 6. Verification Tokens
CREATE TABLE verification_tokens
(
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    purpose     VARCHAR(30) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ(0) NOT NULL,
    consumed_at TIMESTAMPTZ(0),
    request_ip  VARCHAR(45),
    created_at  TIMESTAMPTZ(0) NOT NULL DEFAULT now(),

    CONSTRAINT pk_verification_tokens PRIMARY KEY (id),
    CONSTRAINT fk_verification_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT uq_verification_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT ck_verification_tokens_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),

    CONSTRAINT ck_verification_tokens_expires_at
        CHECK (expires_at > created_at)
);

-- Chỉ cho phép một token chưa consume trên mỗi user và purpose.
CREATE UNIQUE INDEX uq_verification_tokens_active
    ON verification_tokens (user_id, purpose) WHERE consumed_at IS NULL;

CREATE INDEX idx_verification_tokens_expires_at
    ON verification_tokens (expires_at);


-- 7. User Sessions
CREATE TABLE user_sessions
(
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL,
    family_id          UUID        NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    issued_at          TIMESTAMPTZ(0) NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ(0) NOT NULL,
    rotated_at         TIMESTAMPTZ(0),
    revoked_at         TIMESTAMPTZ(0),
    revoke_reason      VARCHAR(30),
    ip_address         VARCHAR(45),
    user_agent         VARCHAR(255),

    CONSTRAINT pk_user_sessions PRIMARY KEY (id),
    CONSTRAINT fk_user_sessions_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT uq_user_sessions_refresh_token_hash
        UNIQUE (refresh_token_hash),

    CONSTRAINT ck_user_sessions_expires_at
        CHECK (expires_at > issued_at),

    CONSTRAINT ck_user_sessions_revoke_reason
        CHECK (
            revoke_reason IS NULL
                OR revoke_reason IN (
                                     'LOGOUT',
                                     'REUSE_DETECTED',
                                     'PASSWORD_CHANGED',
                                     'ADMIN_REVOKED'
                )
            ),

    CONSTRAINT ck_user_sessions_revoked_pair
        CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

CREATE INDEX idx_user_sessions_user_id
    ON user_sessions (user_id);

CREATE INDEX idx_user_sessions_family_id
    ON user_sessions (family_id);

CREATE INDEX idx_user_sessions_expires_at
    ON user_sessions (expires_at);
