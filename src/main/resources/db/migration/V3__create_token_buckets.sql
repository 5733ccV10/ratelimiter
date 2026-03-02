CREATE TABLE token_buckets (
    identity    VARCHAR(255)     NOT NULL,
    resource    VARCHAR(255)     NOT NULL,
    tier        VARCHAR(50)      NOT NULL DEFAULT 'DEFAULT',
    tokens      DOUBLE PRECISION NOT NULL,
    last_refill BIGINT           NOT NULL,   -- Unix epoch seconds

    PRIMARY KEY (identity, resource, tier)
);
