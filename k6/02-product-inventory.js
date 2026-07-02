import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

// PaymentCompletedEvent(product-service)가 orderId/userId/dropId를 java.util.UUID로 역직렬화하므로
// 반드시 표준 36자 UUID 형식이어야 함. 팀 컨벤션(UuidV7Generator)에 맞춰 UUID v7 생성
// (48bit ms 타임스탬프 + 버전 7 + variant 10 + 나머지 랜덤) — 테스트 데이터 용도.
function uuidv7() {
  const tsHex = Date.now().toString(16).padStart(12, '0'); // 48bit 타임스탬프 (12 hex)
  const hexDigit = () => Math.floor(Math.random() * 16).toString(16);
  const randHex = (n) => Array.from({ length: n }, hexDigit).join('');

  const versionNibble = '7';
  const variantNibble = (8 + Math.floor(Math.random() * 4)).toString(16); // 10xx → 8~b

  const hex = tsHex + versionNibble + randHex(3) + variantNibble + randHex(15); // 총 32 hex
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

// product-service / inventory 부하 테스트
//
// 시나리오 A: 드롭 구매 선점 (1,000명 동시 진입 → 재고 N개 선착순)
//   - drop-service 구매 API → product-service 재고 스냅샷 조회 연쇄 부하
//   - 검증: CPU, p99, HikariCP 커넥션 풀
//
// 시나리오 B: 재고 확정 차감 (payment.completed 대량 발행)
//   - Kafka REST Proxy를 통해 payment.completed 이벤트 대량 발행
//   - product-service Consumer가 수신 → 낙관적 락 기반 재고 차감
//   - 검증: Kafka Consumer Lag, STOCK_DEDUCTED/STOCK_FAILED 정합성
//
// 시나리오 C: 중복 방지 (SCENARIO=duplicate)
//   - 동일 유저 1명의 토큰으로 DUPLICATE_CONCURRENCY(기본 50)개 동시 요청
//   - 검증: 202가 정확히 1건, 나머지는 409 + errorCode=DROP-005
//
// 시나리오 D: 캐시 히트율 / TTL / 무효화 검증 (SCENARIO=cache)
//   - Redis 기반 @Cacheable("product")은 히트/미스 통계를 노출하지 않아
//     (RedisCacheManager는 Micrometer CacheMetricsRegistrar 미지원) 응답시간으로 간접 구분
//   - ① 콜드 조회 1회(cache_miss_duration_ms) → ② CACHE_REPEAT회 반복 조회(cache_hit_duration_ms)
//     → miss가 hit보다 뚜렷하게 느려야 정상 (캐시가 실제로 타고 있다는 신호)
//   - ③ payment.completed 1건 발행 → 재고 확정 차감 → 재조회해서 availableQuantity가
//     1 감소했는지 확인 (cache_invalidation_success_rate). confirmDeduct() 이후
//     ProductUpdatedEvent 발행 로직 회귀 방지용 — 실행 전 해당 상품의 캐시가 비어있어야
//     ①의 "콜드 조회"가 의미 있음 (직전에 어드민 API로 상품/재고를 한 번 건드려 evict해두면 됨)
//   - TTL(10분) 자체를 줄일지 여부는 이 스크립트로는 자동 검증 안 됨 — 필요 시
//     실행 후 10분 대기하고 동일 스크립트를 한 번 더 돌려 miss가 재발생하는지 수동 확인
//
// =====================================================================
// 전체 실행 순서 (구매 선점 SCENARIO=purchase 기준, End-to-End)
// =====================================================================
//
// 1. 유저 1,000명 생성 (최초 1회, 또는 users.json이 지워진 뒤 재실행)
//      node k6/generator.js
//    ※ 완료까지 약 7분 소요 (400ms 간격 순차 발사 — Rate Limiter 대응, generator.js 주석 참고)
//
// 2. 관리자 계정으로 상품 + 드롭 생성 (curl 또는 Postman)
//      - POST /api/v1/admin/products  (initialQuantity = 이번에 테스트할 재고 수)
//      - POST /api/v1/admin/drops     (productId, startAt=현재+10초, totalQty)
//      - startAt 지난 뒤 GET /api/v1/admin/drops/{dropId}로 status=OPEN 확인
//
// 3. k6 실행
//      k6 run -e SCENARIO=purchase -e DROP_ID=xxx k6/02-product-inventory.js
//
// 4. 결과 검증 (DB/Redis/Grafana) — 아래 "결과 확인" 섹션 참고
//      - sold_quantity, p_outbox_events, p_failed_event_logs 등 SQL로 직접 확인
//      - Redis purchased/stock 키 확인
//      - Grafana CPU/스레드/Lag 확인
//
// 5. (검증 다 끝난 뒤, 별도로) 테스트 유저 정리
//      node k6/cleanup.js
//
// ⚠️ 주의: 4번(검증)과 5번(cleanup) 사이에 텀을 두세요.
//   cleanup은 절대 3번 k6 실행 뒤에 자동으로 이어붙이지 마세요 (스크립트로 묶지 않는 이유이기도 함).
//   실패 원인 추적이나 DB/Redis 교차 검증은 데이터가 남아있어야만 가능한데,
//   generate→test→cleanup을 한 파이프라인으로 엮으면 실패했을 때 재현하려고
//   처음부터 다시 돌려야 하는 상황이 생깁니다. 검증까지 다 끝났다고 확신이 들 때
//   사람이 직접 cleanup.js를 실행하는 걸 원칙으로 합니다.
//
// =====================================================================
//
// 실행 전 준비:
//   1. 드롭이 OPEN 상태여야 함 (startAt 설정 후 스케줄러 자동 전환 대기)
//   2. 실제 존재하는 dropId, productId 필요
//   3. 환경변수로 전달:
//      k6 run \
//        -e SCENARIO=purchase \
//        -e DROP_ID=xxx \
//        -e PRODUCT_ID=yyy \
//        -e USER_EMAIL=loadtest@test.com \
//        -e USER_PW=Test1234! \
//        k6/02-product-inventory.js
//      # SCENARIO=stock일 때는 PRODUCT_ID 필수, KAFKA_REST_PROXY_URL은 필요 시 override
//      #   (기본값 http://localhost:8090 — drop-service가 8082를 점유하고 있어 compose에서 8090으로 매핑됨)
//
// 결과 확인:
//   Grafana(:13000) → OMC Spring Boot 모니터링 → product-service 선택
//   - CPU 사용률 (70% 이상 지속 시 분리 검토 기준)
//   - HTTP 평균 응답시간 / 에러 요청 수
//   - HikariCP DB 커넥션 풀 (Pending 발생 시 풀 고갈 신호)
//   - Kafka 컨슈머 Lag (50건 이상 1분 지속 시 처리 지연 신호)

const BASE      = __ENV.BASE_URL        || 'http://localhost:8080';
const GW_SECRET = __ENV.GATEWAY_SECRET  || 'local-secret';
const SCENARIO  = __ENV.SCENARIO        || 'purchase'; // 'purchase' | 'stock' | 'duplicate' | 'cache'
const DROP_ID   = __ENV.DROP_ID         || 'CHANGE_ME';
const PRODUCT_ID = __ENV.PRODUCT_ID     || 'CHANGE_ME';
// Kafka REST Proxy 호스트 포트. drop-service가 8082를 이미 점유하고 있어
// docker-compose.yml에서 8090:8082로 매핑되어 있음 — 하드코딩 대신 환경변수로 관리
const KAFKA_REST_PROXY_URL = __ENV.KAFKA_REST_PROXY_URL || 'http://localhost:8090';
// 중복 방지 시나리오: 동시에 몰려드는 VU 수 (= 동일 유저의 동시 중복 요청 수)
const DUPLICATE_CONCURRENCY = parseInt(__ENV.DUPLICATE_CONCURRENCY || '50', 10);
// 캐시 히트율 시나리오: 웜업 후 반복 조회 횟수
const CACHE_REPEAT = parseInt(__ENV.CACHE_REPEAT || '20', 10);

// 커스텀 메트릭
const purchaseDuration  = new Trend('purchase_duration_ms');
const purchaseSuccess   = new Rate('purchase_success_rate');
const stockSentCount    = new Counter('stock_event_sent_count');
const duplicateSuccessCount  = new Counter('duplicate_success_count');   // 202 (선점 성공) — 정확히 1이어야 함
const duplicateRejectedCount = new Counter('duplicate_rejected_count');  // 409 + DROP-005 (중복 구매 차단)
const duplicateUnexpectedCount = new Counter('duplicate_unexpected_count'); // 그 외 예상 못한 응답
// 캐시 히트율 근사 지표: Redis 히트/미스 통계가 없어 응답시간으로 간접 구분
// (미스=DB+Feign 조회 포함, 히트=Redis GET만) — 절대적 %가 아니라 상대적 분포로 해석
const cacheMissDuration = new Trend('cache_miss_duration_ms'); // 캐시 무효화 직후 첫 조회
const cacheHitDuration  = new Trend('cache_hit_duration_ms');  // 이후 반복 조회
const cacheInvalidationOk = new Rate('cache_invalidation_success_rate'); // 차감 후 캐시가 갱신됐는지
const userData = new SharedArray('users', function () {
  return JSON.parse(open('./users.json'));
});

// =====================================================================
// 시나리오 A: 드롭 구매 선점
// =====================================================================
export const purchaseOptions = {
  scenarios: {
    drop_purchase: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s',  target: 1000 }, // 5초 안에 1,000명 진입 (동시성 극대화)
        { duration: '10s', target: 1000 }, // 10초 유지
        { duration: '5s',  target: 0 },    // 감소
      ],
    },
  },
  thresholds: {
    http_req_duration:    ['p(99)<2000'],   // p99 2초 이내
    purchase_success_rate: ['rate>0.09'],   // 최소 9% 성공 (재고 100개 / 1,000명 기준)
  },
};

// =====================================================================
// 시나리오 B: 재고 확정 차감 (Kafka REST Proxy)
// =====================================================================
export const stockOptions = {
  scenarios: {
    stock_deduct: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: 1000, // 1,000건의 payment.completed 발행
      maxDuration: '2m',
    },
  },
  thresholds: {
    stock_event_sent_count: ['count>=1000'], // 1,000건 전송 확인
  },
};

// =====================================================================
// 시나리오 C: 중복 방지 (동일 유저가 동시에 여러 번 요청 → 1번만 성공해야 함)
// =====================================================================
export const duplicateOptions = {
  scenarios: {
    duplicate_purchase: {
      executor: 'shared-iterations',
      vus: DUPLICATE_CONCURRENCY,
      iterations: DUPLICATE_CONCURRENCY, // 동일 유저로 정확히 DUPLICATE_CONCURRENCY번 동시 요청
      maxDuration: '30s',
    },
  },
  thresholds: {
    duplicate_success_count: ['count>=1', 'count<=1'], // 정확히 1건만 성공해야 함
  },
};

// =====================================================================
// 시나리오 D: 캐시 히트율 / TTL / 무효화 검증
//   ① 캐시 무효화 직후(콜드) 1회 조회 → miss 추정
//   ② 곧바로 CACHE_REPEAT회 반복 조회 → hit 추정 (응답시간으로 간접 구분)
//   ③ payment.completed 1건 발행해 실제 재고 차감 → 곧바로 재조회해서
//      availableQuantity가 갱신됐는지 확인 (캐시 무효화 결함 회귀 방지)
// =====================================================================
export const cacheOptions = {
  scenarios: {
    cache_check: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1, // 동시성이 아니라 순서가 중요한 시나리오라 단일 VU로 순차 실행
      maxDuration: '30s',
    },
  },
  thresholds: {
    cache_invalidation_success_rate: ['rate>=1'], // 무효화 실패는 0건이어야 함
  },
};

// SCENARIO 환경변수에 따라 옵션 선택
export const options =
    SCENARIO === 'stock'     ? stockOptions :
    SCENARIO === 'duplicate' ? duplicateOptions :
    SCENARIO === 'cache'     ? cacheOptions :
    purchaseOptions;

// =====================================================================
// setup: 1,000명의 테스트 유저 생성 및 토큰 확보 (구매 선점 시나리오에서 사용)
//        중복 방지 시나리오는 동일 유저 1명의 토큰만 필요
// =====================================================================
export function setup() {
  if (SCENARIO === 'stock' || SCENARIO === 'cache') return {};
  if (SCENARIO === 'duplicate') return { token: userData[0].token };
  return { tokens: userData.map(u => u.token) };
}


// =====================================================================
// 시나리오 A 실행: 드롭 구매 선점
// =====================================================================
function runPurchase(data) {

  const vuIndex = exec.vu.idInTest - 1;
  const token = data.tokens[vuIndex];

  const params = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-Gateway-Secret': GW_SECRET,
    },
  };

  const res = http.post(`${BASE}/api/v1/drops/${DROP_ID}/purchase`, null, params);

  const ok = check(res, {
    '202 Accepted (선점 성공)': (r) => r.status === 202,
    '409 Conflict (재고 소진 — 정상 비즈니스 응답)': (r) => r.status === 409,
  });

  // 202(선점 성공)만 성공률로 집계
  purchaseSuccess.add(res.status === 202);
  purchaseDuration.add(res.timings.duration);
}

// =====================================================================
// 시나리오 B 실행: payment.completed 발행 (Kafka REST Proxy)
// =====================================================================
function runStockDeduct() {
  const eventId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;

  const payload = JSON.stringify({
    records: [{
      value: {
        eventId:     eventId,
        orderId:     uuidv7(),
        productId:   PRODUCT_ID,
        userId:      uuidv7(),
        dropId:      uuidv7(),
        finalAmount: 100000,
      },
    }],
  });

  const res = http.post(
      `${KAFKA_REST_PROXY_URL}/topics/payment.completed`,
      payload,
      { headers: { 'Content-Type': 'application/vnd.kafka.json.v2+json' } }
  );

  const ok = check(res, {
    'Kafka 발행 성공 (2xx)': (r) => r.status >= 200 && r.status < 300,
  });

  if (ok) stockSentCount.add(1);
  sleep(0.1);
}

// =====================================================================
// 시나리오 C 실행: 동일 유저 동시 중복 요청
// =====================================================================
function runDuplicate(data) {
  const params = {
    headers: {
      'Authorization': `Bearer ${data.token}`,
      'X-Gateway-Secret': GW_SECRET,
    },
  };

  const res = http.post(`${BASE}/api/v1/drops/${DROP_ID}/purchase`, null, params);

  let errorCode = null;
  try {
    errorCode = res.json('errorCode');
  } catch (e) {
    // 응답 파싱 실패는 아래 unexpected 분기로 처리
  }

  check(res, {
    '202 Accepted (선점 성공 — 최초 1건)': (r) => r.status === 202,
    '409 DROP-005 (중복 구매 차단)': (r) => r.status === 409 && errorCode === 'DROP-005',
  });

  if (res.status === 202) {
    duplicateSuccessCount.add(1);
  } else if (res.status === 409 && errorCode === 'DROP-005') {
    duplicateRejectedCount.add(1);
  } else {
    duplicateUnexpectedCount.add(1);
    console.warn(`[duplicate] 예상 못한 응답: status=${res.status}, body=${res.body}`);
  }
}

// =====================================================================
// 시나리오 D 실행: 캐시 히트율 / 무효화 검증
// =====================================================================
function runCacheCheck() {
  const params = { headers: { 'X-Gateway-Secret': GW_SECRET } };
  const productUrl = `${BASE}/api/v1/products/${PRODUCT_ID}`;

  // ① 콜드 조회 (미스 추정) — 직전에 캐시를 비워둔 상태에서 실행해야 의미 있음
  //    (예: 어드민 API로 상품/재고를 한 번 수정해 캐시를 evict한 뒤 바로 실행)
  const missRes = http.get(productUrl, params);
  check(missRes, { '콜드 조회 200 OK': (r) => r.status === 200 });
  cacheMissDuration.add(missRes.timings.duration);

  let availableBefore = null;
  try { availableBefore = missRes.json('data.availableQuantity'); } catch (e) {}

  // ② 반복 조회 (히트 추정) — 미스보다 눈에 띄게 빨라야 정상
  for (let i = 0; i < CACHE_REPEAT; i++) {
    const hitRes = http.get(productUrl, params);
    check(hitRes, { '반복 조회 200 OK': (r) => r.status === 200 });
    cacheHitDuration.add(hitRes.timings.duration);
    sleep(0.05);
  }

  // ③ 실제 결제 완료 이벤트 1건 발행 → 재고 차감 → 캐시 무효화 여부 확인
  const eventId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const payload = JSON.stringify({
    records: [{
      value: {
        eventId,
        orderId:     uuidv7(),
        productId:   PRODUCT_ID,
        userId:      uuidv7(),
        dropId:      uuidv7(),
        finalAmount: 100000,
      },
    }],
  });
  http.post(`${KAFKA_REST_PROXY_URL}/topics/payment.completed`, payload,
      { headers: { 'Content-Type': 'application/vnd.kafka.json.v2+json' } });

  // Kafka 컨슈머가 처리할 시간을 대기 (재고 확정 차감은 보통 수십~수백ms 내 완료)
  sleep(2);

  const afterRes = http.get(productUrl, params);
  let availableAfter = null;
  try { availableAfter = afterRes.json('data.availableQuantity'); } catch (e) {}

  const invalidated = availableBefore !== null && availableAfter !== null && availableAfter === availableBefore - 1;
  check(afterRes, {
    '캐시 무효화 후 availableQuantity가 1 감소': () => invalidated,
  });
  cacheInvalidationOk.add(invalidated);

  console.log(`[cache] before=${availableBefore}, after=${availableAfter}, invalidated=${invalidated}`);
}

// =====================================================================
// 메인 실행 함수
// =====================================================================
export default function (data) {
  if (SCENARIO === 'stock') {
    runStockDeduct();
  } else if (SCENARIO === 'duplicate') {
    runDuplicate(data);
  } else if (SCENARIO === 'cache') {
    runCacheCheck();
  } else {
    runPurchase(data);
  }
}