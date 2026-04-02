-- 012_system_rank.sql
-- Systemfirma-Rang: NULL = normaler Partner, 1 = fuehrend, 2+ = Tochter

ALTER TABLE business_partner ADD COLUMN system_rank SMALLINT UNIQUE;

COMMENT ON COLUMN business_partner.system_rank IS 'Systemfirma-Rang: 1=fuehrend, 2+=Tochter, NULL=normaler Partner';
