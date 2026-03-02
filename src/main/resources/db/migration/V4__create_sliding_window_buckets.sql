CREATE TABLE sliding_window_buckets (
    identity    VARCHAR(255) NOT NULL,
    resource    VARCHAR(255) NOT NULL,
    tier        VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT',
    bucket_time BIGINT       NOT NULL,   -- Unix epoch seconds, truncated to second
    count       INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (identity, resource, tier, bucket_time)
);

CREATE INDEX idx_sw_bucket_time ON sliding_window_buckets(identity, resource, tier, bucket_time);
