-- KEYS[1] = holds:{dropId}
-- KEYS[2] = stock:{dropId}
-- ARGV[1] = orderId
local removed = redis.call('ZREM', KEYS[1], ARGV[1])
if removed == 1 then
    redis.call('INCR', KEYS[2])
end
return removed
