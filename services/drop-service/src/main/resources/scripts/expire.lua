-- KEYS[1] = holds:{dropId}
-- KEYS[2] = stock:{dropId}
-- KEYS[3] = sold_out:{dropId}  품절 플래그 (재고 복구 시 삭제)
-- ARGV[1] = orderId
local removed = redis.call('ZREM', KEYS[1], ARGV[1])
if removed == 1 then
    redis.call('INCR', KEYS[2])
    redis.call('DEL', KEYS[3])
end
return removed
