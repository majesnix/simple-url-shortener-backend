ALTER TABLE t_url ALTER COLUMN short TYPE TEXT;
ALTER TABLE t_url ADD CONSTRAINT t_url_short_length CHECK (char_length(short) <= 50);
