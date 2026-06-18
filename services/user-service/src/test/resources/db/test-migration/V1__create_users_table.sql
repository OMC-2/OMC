CREATE SCHEMA IF NOT EXISTS user_db;

CREATE TABLE user_db.p_users
(
    user_id     UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    keycloak_id VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    nickname    VARCHAR(50)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    slack_id    VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(100),
    deleted_at  TIMESTAMPTZ,
    deleted_by  VARCHAR(100)
);
