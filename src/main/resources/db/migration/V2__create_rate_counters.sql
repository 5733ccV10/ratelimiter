CREATE TABLE rate_counters (
    identity     VARCHAR(255) NOT NULL,
    resource     VARCHAR(255) NOT NULL,
    tier         VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT',
    window_start BIGINT       NOT NULL,   -- Unix epoch seconds
    count        INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (identity, resource, tier, window_start)
);

CREATE INDEX idx_rate_counters_lookup ON rate_counters(identity, resource, tier, window_start);
