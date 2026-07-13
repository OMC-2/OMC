-- KEYS[1] = purchased:{dropId}         구매자 Set
-- KEYS[2] = stock:{dropId}             재고 카운터
-- KEYS[3] = holds:{dropId}             선점 ZSet (score = 만료 epoch)
-- KEYS[4] = queue:{dropId}             순번 카운터
-- KEYS[5] = drop:{dropId}:status       OPEN 플래그
-- KEYS[6] = hold_ttl:{dropId}          holdTtlSec
-- KEYS[7] = product_id:{dropId}        productId
-- KEYS[8] = sold_out:{dropId}          품절 플래그 (Gateway 조기 차단용)
-- ARGV[1] = userId
-- ARGV[2] = orderId
-- ARGV[3] = dropId

-- OPEN 체크
local status = redis.call('GET', KEYS[5])
if status ~= 'OPEN' then
    return -3
end

-- holdTtlSec 조회
local holdTtlSec = tonumber(redis.call('GET', KEYS[6])) or 600

-- productId 존재 여부 확인
local productId = redis.call('GET', KEYS[7])
if not productId then
    return -4
end

-- 중복 구매 체크를 품절 체크보다 먼저: UX 개선 (이미 신청한 사용자에게 정확한 메시지 전달)
if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
    return -2
end

-- 재고 확인 (음수 방어: DECR 과호출로 인한 음수 재고 처리)
local stock = tonumber(redis.call('GET', KEYS[2]))
if stock == nil or stock <= 0 then
    return -1
end

-- 원자적 선점 처리
local remaining = redis.call('DECR', KEYS[2])
if remaining <= 0 then
    redis.call('SET', KEYS[8], '1')
end
redis.call('SADD', KEYS[1], ARGV[1])

-- Redis 서버 시간 기준 만료 epoch 계산 (다중 인스턴스 시계 차이 방지)
local now = redis.call('TIME')
local expireEpoch = tonumber(now[1]) + holdTtlSec
redis.call('ZADD', KEYS[3], expireEpoch, ARGV[2])

-- 순번 발급 (1 이상의 양수)
return redis.call('INCR', KEYS[4])
