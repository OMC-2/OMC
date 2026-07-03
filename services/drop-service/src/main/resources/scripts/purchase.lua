-- KEYS[1] = purchased:{dropId}         구매자 Set
-- KEYS[2] = stock:{dropId}             재고 카운터
-- KEYS[3] = holds:{dropId}             선점 ZSet (score = 만료 epoch)
-- KEYS[4] = queue:{dropId}             순번 카운터
-- KEYS[5] = stream:purchase:confirmed  이벤트 Stream
-- KEYS[6] = drop:{dropId}:status       OPEN 플래그
-- KEYS[7] = hold_ttl:{dropId}          holdTtlSec
-- KEYS[8] = product_id:{dropId}        productId
-- ARGV[1] = userId
-- ARGV[2] = orderId
-- ARGV[3] = dropId
-- ARGV[4] = eventId

-- OPEN 체크 (Java 측 개별 GET 제거 → Lua 내부로 통합)
local status = redis.call('GET', KEYS[6])
if status ~= 'OPEN' then
    return -3
end

-- holdTtlSec 조회 (Java 측 개별 GET 제거)
local holdTtlSec = tonumber(redis.call('GET', KEYS[7])) or 600

-- productId 조회 (Java 측 개별 GET 제거)
local productId = redis.call('GET', KEYS[8])
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
redis.call('DECR', KEYS[2])
redis.call('SADD', KEYS[1], ARGV[1])

-- Redis 서버 시간 기준 만료 epoch 계산 (다중 인스턴스 시계 차이 방지)
local now = redis.call('TIME')
local expireEpoch = tonumber(now[1]) + holdTtlSec
redis.call('ZADD', KEYS[3], expireEpoch, ARGV[2])

-- 선점과 원자적으로 Stream에 이벤트 적재 (XACK 전까지 영속 보장)
redis.call('XADD', KEYS[5], 'MAXLEN', '~', '10000', '*',
    'eventId',      ARGV[4],
    'orderId',      ARGV[2],
    'dropId',       ARGV[3],
    'userId',       ARGV[1],
    'productId',    productId,
    'holdExpiresAt', tostring(expireEpoch)
)

-- 순번 발급 (1 이상의 양수)
return redis.call('INCR', KEYS[4])
