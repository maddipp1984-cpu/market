-- 013_commodity_group.sql
-- Warengruppen (Commodity Groups)

CREATE TABLE ts_commodity_group (
    commodity_group_id  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                TEXT NOT NULL UNIQUE
);

COMMENT ON TABLE ts_commodity_group IS 'Warengruppen: Gruppierung von Waren (z.B. Gas, Strom)';
