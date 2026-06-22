-- KEYS[1] = holds:{dropId}     선점 ZSet
-- KEYS[2] = stock:{dropId}     재고 카운터
-- KEYS[3] = purchased:{dropId} 구매자 Set
-- ARGV[1] = orderId
-- ARGV[2] = userId

-- hold가 있을 때만 재고·구매자 복구 (멱등성: 이미 없으면 0 반환하고 중단)
local removed = redis.call('ZREM', KEYS[1], ARGV[1])
if removed == 1 then
    redis.call('INCR', KEYS[2])
    redis.call('SREM', KEYS[3], ARGV[2])
end
return removed
