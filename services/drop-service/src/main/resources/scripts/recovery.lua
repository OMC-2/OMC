-- KEYS[1] = holds:{dropId}     선점 ZSet
-- KEYS[2] = stock:{dropId}     재고 카운터
-- KEYS[3] = purchased:{dropId} 구매자 Set
-- KEYS[4] = sold_out:{dropId}  품절 플래그 (재고 복구 시 삭제)
-- ARGV[1] = orderId
-- ARGV[2] = userId

local removed = redis.call('ZREM', KEYS[1], ARGV[1])
if removed == 1 then
    redis.call('INCR', KEYS[2])
    redis.call('DEL', KEYS[4])
    redis.call('SREM', KEYS[3], ARGV[2])
end
return removed
