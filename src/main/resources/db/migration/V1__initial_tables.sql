CREATE TABLE IF NOT EXISTS t_url (
    id BIGSERIAL PRIMARY KEY,
    short varchar(50) NOT NULL,
    long text NOT NULL
);

CREATE INDEX t_url_short_idx ON t_url(short);