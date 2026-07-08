import http, { setResponseCallback } from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

// 쿠폰 동시 발급 부하 테스트
//
// 시나리오: 선착순 쿠폰 발급 (동시 N명 → 재고 M개)
//   - setup()에서 각 사용자 JWT로 AES 티켓 미리 발급
//   - 부하 시 X-Coupon-Ticket 헤더로 요청 → Gateway ES256 검증 스킵
//   - 모든 시나리오는 서로 다른 사용자가 딱 한 번씩 발급을 시도
//   - 재고를 사용자 수 이상으로 준비하므로 202 외 응답은 실패로 기록
//
// 실행 방법 (run.sh 권장):
//   ./k6/run.sh coupon pool-size-10
//
// 수동 실행:
//   k6 run --env SCENARIO=smoke  --env COUPON_ID=<uuid> k6/03-coupon-issue.js
//   k6 run --env SCENARIO=load   --env COUPON_ID=<uuid> k6/03-coupon-issue.js
//   k6 run --env SCENARIO=stress --env VUS=200 --env COUPON_ID=<uuid> k6/03-coupon-issue.js
//   k6 run --env SCENARIO=spike  --env COUPON_ID=<uuid> k6/03-coupon-issue.js

const BASE      = __ENV.BASE_URL       || 'http://localhost:8080';
const GW_SECRET = __ENV.GATEWAY_SECRET || 'local-secret';
const SCENARIO  = __ENV.SCENARIO       || 'smoke';
const COUPON_ID = __ENV.COUPON_ID      || 'CHANGE_ME';
const VUS       = parseInt(__ENV.VUS   || '200', 10);
const P95_LIMIT = __ENV.P95_LIMIT || '10000';

const issueDuration    = new Trend('coupon_issue_duration_ms');
const issueSuccessRate = new Rate('coupon_issue_success_rate');

const userData = new SharedArray('users', function () {
  return JSON.parse(open('./users.json'));
});

// 티켓 캐시 — ticket_cache.json이 있으면 로드, 없으면 null
// run.sh의 ensure_ticket_cache()가 발급·갱신을 담당 (k6 컨텍스트 격리 문제로 k6에서 저장 불가)
let ticketCache = null;
try {
  ticketCache = JSON.parse(open('./ticket_cache.json'));
} catch (_) { /* 캐시 없음 — setup()에서 직접 발급 (run.sh 없이 실행한 경우) */ }

const TICKET_TTL_SEC = 86400; // 서버 TTL과 동일 (24h)

// =====================================================================
// Smoke Test: 5명 × 1회 (스크립트/인증/라우팅 확인용)
// 쿠폰은 1인 1발급 → 반복이 의미 없으므로 iteration 기반으로 실행
// =====================================================================
export const smokeOptions = {
  scenarios: {
    coupon_smoke: {
      executor: 'shared-iterations',
      vus: 5,
      iterations: 5,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${P95_LIMIT}`],
    coupon_issue_success_rate: ['rate>0.99'],
  },
};

// =====================================================================
// Load Test: VUS명이 동시에 쿠폰 발급 시도 (1인 1회)
// =====================================================================
export const loadOptions = {
  scenarios: {
    coupon_load: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: VUS,
      maxDuration: '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: [`p(95)<${P95_LIMIT}`],
    coupon_issue_success_rate: ['rate>0.99'],
  },
};

// =====================================================================
// Stress Test: VUS명의 고유 사용자가 각 1회씩 동시 발급
// ramping-vus는 같은 VU가 409 중복 요청을 반복해 실제 발급 부하를 왜곡하므로 사용하지 않는다.
// =====================================================================
export const stressOptions = {
  scenarios: {
    coupon_stress: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: VUS,
      maxDuration: '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${P95_LIMIT}`],
    coupon_issue_success_rate: ['rate>0.99'],
  },
};

// =====================================================================
// Spike Test: 200명이 준비 없이 즉시 동시 발급 (1인 1회)
// =====================================================================
export const spikeOptions = {
  scenarios: {
    coupon_spike: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: VUS,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: [`p(95)<${P95_LIMIT}`],
    coupon_issue_success_rate: ['rate>0.99'],
  },
};

const baseOptions =
  SCENARIO === 'load'   ? loadOptions   :
  SCENARIO === 'stress' ? stressOptions :
  SCENARIO === 'spike'  ? spikeOptions  :
  smokeOptions;

export const options = { ...baseOptions, setupTimeout: '3m' };

// setup(): AES 티켓을 준비한 뒤 반환
// 티켓은 userId+expiry만 포함 (couponId 없음) → 24시간 동안 어떤 쿠폰에도 재사용 가능
// ticket_cache.json이 유효하면 즉시 재사용, 없거나 만료됐으면 새로 발급 후 저장
export function setup() {
  const now = Math.floor(Date.now() / 1000);

  // 캐시 유효성 확인: 24시간 이내 + 유저 수 충분
  if (ticketCache &&
      ticketCache.tickets &&
      ticketCache.tickets.length >= userData.length &&
      (now - ticketCache.issued_at) < TICKET_TTL_SEC) {
    const ageH = Math.round((now - ticketCache.issued_at) / 3600);
    console.log(`[setup] 티켓 캐시 유효 (${ageH}시간 전 발급). 재발급 없이 바로 시작합니다.`);
    return { tickets: ticketCache.tickets, tokens: userData.map(u => u.token) };
  }

  // 캐시 없음 또는 만료 — 새로 발급
  const total = userData.length;
  console.log(`[setup] 티켓 발급 시작 (총 ${total}명)...`);
  const tickets = [];
  for (let i = 0; i < total; i++) {
    const u = userData[i];
    const res = http.post(
      `${BASE}/api/v1/coupons/${COUPON_ID}/ticket`,
      null,
      {
        headers: {
          'Authorization':    `Bearer ${u.token}`,
          'X-Gateway-Secret': GW_SECRET,
          'Content-Type':     'application/json',
        },
      }
    );
    if (res.status === 200) {
      tickets.push(JSON.parse(res.body).data.ticket);
    } else {
      tickets.push(null);
    }
    if ((i + 1) % 100 === 0) {
      console.log(`[setup] 티켓 발급 중... ${i + 1}/${total}`);
    }
  }

  if (__ENV.SKIP_SLEEP !== 'true') {
    console.log('[setup] AES 티켓 발급 완료. 60초 대기 후 부하 시작...');
    sleep(60);
  }

  return { tickets, tokens: userData.map(u => u.token) };
}

export default function (data) {
  setResponseCallback(http.expectedStatuses(202));

  const idx = exec.scenario.iterationInTest % data.tickets.length;

  const ticket = data.tickets[idx % data.tickets.length];
  const token  = data.tokens[idx % data.tokens.length];

  let res;
  if (ticket) {
    // AES 티켓 방식: ES256 검증 없이 Gateway에서 AES 복호화로 처리
    res = http.post(
      `${BASE}/api/v1/coupons/${COUPON_ID}/issue`,
      null,
      {
        headers: {
          'X-Coupon-Ticket':  ticket,
          'X-Gateway-Secret': GW_SECRET,
          'Content-Type':     'application/json',
        },
      }
    );
  } else {
    // 티켓 발급 실패 시 JWT 폴백
    res = http.post(
      `${BASE}/api/v1/coupons/${COUPON_ID}/issue`,
      null,
      {
        headers: {
          'Authorization':    `Bearer ${token}`,
          'X-Gateway-Secret': GW_SECRET,
          'Content-Type':     'application/json',
        },
      }
    );
  }

  check(res, {
    '202 Accepted (발급 성공)':       (r) => r.status === 202,
    '500 에러 없음':                  (r) => r.status !== 500,
  });

  issueSuccessRate.add(res.status === 202);
  issueDuration.add(res.timings.duration);
}
