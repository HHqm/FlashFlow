-- KEYS: 1 stock, 2 buyer purchased count, 3 idempotency key -> order number, 4 per-second rate counter
-- ARGV: 1 order number, 2 TTL seconds, 3 quantity, 4 purchase limit, 5 requests per second
-- Rate limiting lives here so an order costs one Redis round trip instead of two.
local attempts = redis.call('INCR', KEYS[4])
if attempts == 1 then
    redis.call('EXPIRE', KEYS[4], 2)
end
if attempts > tonumber(ARGV[5]) then
    return 'RATE_LIMITED'
end

local existing_order = redis.call('GET', KEYS[3])
if existing_order then
    return 'DUPLICATE|' .. existing_order
end

local quantity = tonumber(ARGV[3])
local purchase_limit = tonumber(ARGV[4])
local purchased_raw = redis.call('GET', KEYS[2])
local purchased = tonumber(purchased_raw or '0')
-- Compatible with purchaser keys written by v1, whose value was an order number.
if purchased_raw and not purchased then
    purchased = purchase_limit
end

if purchased + quantity > purchase_limit then
    return 'LIMIT_EXCEEDED'
end

local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock < quantity then
    return 'SOLD_OUT'
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('INCRBY', KEYS[2], quantity)
redis.call('EXPIRE', KEYS[2], ARGV[2])
redis.call('SET', KEYS[3], ARGV[1], 'EX', ARGV[2])
return 'RESERVED|' .. ARGV[1]
