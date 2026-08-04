local request_order = redis.call('GET', KEYS[3])
if request_order ~= ARGV[1] then
    return 0
end

local quantity = tonumber(ARGV[2])
redis.call('INCRBY', KEYS[1], quantity)
local purchased = tonumber(redis.call('GET', KEYS[2]) or '0')
if purchased <= quantity then
    redis.call('DEL', KEYS[2])
else
    redis.call('DECRBY', KEYS[2], quantity)
end
redis.call('DEL', KEYS[3])

return 1
