CREATE TABLE user_db.p_user_addresses
(
    address_id     UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES user_db.p_users (user_id),
    recipient_name VARCHAR(50)  NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    zip_code       VARCHAR(10)  NOT NULL,
    address        VARCHAR(200) NOT NULL,
    address_detail VARCHAR(100),
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ,
    updated_by     VARCHAR(100),
    deleted_at     TIMESTAMPTZ,
    deleted_by     VARCHAR(100)
);
