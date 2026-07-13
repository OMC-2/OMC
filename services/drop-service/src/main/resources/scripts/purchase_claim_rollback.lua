-- KEYS[1] = holds:{dropId}
-- KEYS[2] = stock:{dropId}
-- KEYS[3] = purchased:{dropId}
-- KEYS[4] = sold_out:{dropId}
-- ARGV[1] = orderId
-- ARGV[2] = userId

local removed = redis.call('ZREM', KEYS[1], ARGV[1])
if removed == 1 then
    redis.call('INCR', KEYS[2])
    redis.call('DEL', KEYS[4])
end

-- Redis 선점은 성공했지만 DB reservation/outbox 저장에 실패한 경우에만 사용한다.
-- 아직 유효한 DB reservation이 없으므로 hold가 이미 만료됐더라도 사용자 선점 marker를 제거한다.
redis.call('SREM', KEYS[3], ARGV[2])
return removed
