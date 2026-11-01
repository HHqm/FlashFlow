-- Creates load-test buyers lt_000001 .. lt_<@users> with one default shipping address each.
-- The password hash matches nothing, so these accounts cannot log in; k6 uses tokens minted by
-- mint-tokens.py instead. Safe to run repeatedly.
SET SESSION cte_max_recursion_depth = 1000000;

INSERT IGNORE INTO app_user (username, password_hash, role, enabled, created_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @users
)
SELECT CONCAT('lt_', LPAD(n, 6, '0')), 'load-test-account-without-password', 'USER', TRUE, UTC_TIMESTAMP(6)
FROM seq;

INSERT INTO shipping_address (
    user_id, recipient_name, phone, province, city, district, detail_address,
    postal_code, default_address, created_at, updated_at)
SELECT u.id, CONCAT('Buyer ', u.id), '13800000000', 'Shanghai', 'Shanghai', 'Pudong',
       CONCAT('Load Test Road ', u.id), '200000', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM app_user u
WHERE u.username LIKE 'lt\_%'
  AND NOT EXISTS (SELECT 1 FROM shipping_address a WHERE a.user_id = u.id);

SELECT u.id, u.username, MIN(a.id)
FROM app_user u
JOIN shipping_address a ON a.user_id = u.id
WHERE u.username LIKE 'lt\_%'
GROUP BY u.id, u.username
ORDER BY u.id
LIMIT 1000000;
