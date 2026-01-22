-- ============================================================
-- Migration V4: Fix admin_actions ip_address column type
-- ============================================================
-- Change ip_address from INET to VARCHAR to simplify Hibernate mapping
-- ============================================================

ALTER TABLE admin_actions 
ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::VARCHAR;

COMMENT ON COLUMN admin_actions.ip_address IS 'IP address of the admin performing the action (IPv4 or IPv6)';
