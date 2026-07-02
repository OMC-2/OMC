import http, { setResponseCallback } from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

// 쿠폰 동시 발급 부하 테스트
//
// 시나리오: 선착순 쿠폰 발급 (동시 N명 → 재고 M개)
//   - 각 VU는 자신의 JWT 토큰으로 쿠폰 발급 요청
//   - 201(발급 성공) 또는 409(재고 소진/중복) 외 응답은 에러로 기록
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

const issueDuration    = new Trend('coupon_issue_duration_ms');
const issueSuccessRate = new Rate('coupon_issue_success_rate');

const userData = new SharedArray('users', function () {
  return JSON.parse(open('./users.json'));
});

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
    // smoke 목적: 라우팅/인증 동작 확인 → 에러율만 체크, 응답시간은 측정만
    http_req_failed: ['rate<0.01'],
  },
};

// =====================================================================
// Load Test: 0 → 100명 ramp-up → 1분 유지
// =====================================================================
export const loadOptions = {
  scenarios: {
    coupon_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '60s', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // 개선 전 HikariCP pool-size 10 기준 → 일부 타임아웃 허용, 5% 미만 기준
    // pool-size 50으로 개선 후에는 1% 미만으로 줄어야 함 (비교 포인트)
    http_req_failed: ['rate<0.05'],
    // p95 응답시간은 임계값 없이 측정만 (HikariCP 개선 전/후 비교용)
  },
};

// =====================================================================
// Stress Test: 0 → VUS명 ramp-up → 1분 유지 (VUS 환경변수로 레벨 지정)
// 200 / 400 / 600 / 800 / 1000명 순차 실행 (run.sh에서 레벨별 호출)
// =====================================================================
export const stressOptions = {
  scenarios: {
    coupon_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: VUS },
        { duration: '60s', target: VUS },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

// =====================================================================
// Spike Test: 0 → 200명 순간 급증 (5초) → 0명
// =====================================================================
export const spikeOptions = {
  scenarios: {
    coupon_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s',  target: 200 },
        { duration: '30s', target: 200 },
        { duration: '5s',  target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export const options =
  SCENARIO === 'load'   ? loadOptions   :
  SCENARIO === 'stress' ? stressOptions :
  SCENARIO === 'spike'  ? spikeOptions  :
  smokeOptions;

export function setup() {
  return { tokens: userData.map(u => u.token) };
}

export default function (data) {
  // 201(발급 성공), 409(재고 소진/중복)은 정상 비즈니스 응답 → http_req_failed 카운트 제외
  // VU별로 설정해야 하므로 default 함수 안에서 호출
  setResponseCallback(http.expectedStatuses(201, 409));

  const vuIndex = exec.vu.idInTest - 1;
  const token   = data.tokens[vuIndex % data.tokens.length];

  const res = http.post(
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

  check(res, {
    '201 Created (발급 성공)':       (r) => r.status === 201,
    '409 Conflict (재고 소진/중복)':  (r) => r.status === 409,
    '500 에러 없음':                  (r) => r.status !== 500,
  });

  issueSuccessRate.add(res.status === 201 || res.status === 409);
  issueDuration.add(res.timings.duration);
}
