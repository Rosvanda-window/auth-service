CREATE TABLE users (
                       id              UUID PRIMARY KEY,
                       keycloak_id     UUID NOT NULL UNIQUE,
                       full_name       VARCHAR(100) NOT NULL,
                       email           VARCHAR(255) NOT NULL UNIQUE,
                       status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                       created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                       last_login_at   TIMESTAMP
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE otp_verification (
                                  id                  UUID PRIMARY KEY,
                                  email               VARCHAR(255) NOT NULL,
                                  full_name           VARCHAR(100),
                                  otp_hash            VARCHAR(255) NOT NULL,
                                  purpose             VARCHAR(20) NOT NULL,
                                  attempts            INT NOT NULL DEFAULT 0,
                                  verified            BOOLEAN NOT NULL DEFAULT FALSE,
                                  verification_token  VARCHAR(255) UNIQUE,
                                  expires_at          TIMESTAMP NOT NULL,
                                  token_expires_at    TIMESTAMP,
                                  created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_email_purpose ON otp_verification (email, purpose);