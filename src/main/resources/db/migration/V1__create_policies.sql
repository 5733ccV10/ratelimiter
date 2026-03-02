CREATE TABLE policies (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    identity_type  VARCHAR(20)  NOT NULL,   -- USER, API_KEY, IP, GLOBAL
    resource       VARCHAR(255) NOT NULL,
    strategy       VARCHAR(20)  NOT NULL,   -- FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET
    limit_value    INT          NOT NULL,
    window_seconds INT          NOT NULL,
    burst          INT          NOT NULL DEFAULT 0,
    tier           VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT',
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_policy UNIQUE (identity_type, resource, tier)
);

CREATE INDEX idx_policies_resource ON policies(resource);
