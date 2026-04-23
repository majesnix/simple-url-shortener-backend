DROP INDEX IF EXISTS t_url_short_idx;

ALTER TABLE t_url
    ADD CONSTRAINT t_url_short_unique UNIQUE (short);
