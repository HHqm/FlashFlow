-- Compare-and-set: only repair the cached stock when nobody touched it since it was observed.
local current = redis.call('GET', KEYS[1])
if not current then
    return -1
end
if current ~= ARGV[1] then
    return 0
end
redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
return 1
