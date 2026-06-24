-- KEYS[1] = stock:{dropId}
-- KEYS[2] = drop:{dropId}:status
-- KEYS[3] = hold_ttl:{dropId}
-- KEYS[4] = product_id:{dropId}
-- ARGV[1] = totalQty
-- ARGV[2] = holdTtlSec
-- ARGV[3] = productId

-- 이미 초기화된 drop이면 멱등 처리 (재시작/재배포 시 덮어쓰기 방지)
if redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end

redis.call('SET', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], 'OPEN')
redis.call('SET', KEYS[3], ARGV[2])
redis.call('SET', KEYS[4], ARGV[3])

return 1
