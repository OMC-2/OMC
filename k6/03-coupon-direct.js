import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

// 쿠폰 서비스 직접 부하 테스트 (게이트웨이 우회)
// JWT 검증 없이 X-Gateway-Secret + X-User-Id + X-User-Role 헤더로 직접 인증
//
// 실행: k6 run --env COUPON_ID=<uuid> k6/03-coupon-direct.js

const BASE_URL  = __ENV.BASE_URL      || 'http://localhost:8087';
const GW_SECRET = __ENV.GW_SECRET     || 'local-secret';
const COUPON_ID = __ENV.COUPON_ID     || 'CHANGE_ME';
const VUS       = parseInt(__ENV.VUS  || '100', 10);
const ITERS     = parseInt(__ENV.ITERS || '1000', 10);

const issueDuration    = new Trend('coupon_issue_duration_ms');
const issueSuccessRate = new Rate('coupon_issue_success_rate');

const userIds = new SharedArray('userIds', function () {
  return JSON.parse(open('./user_ids.json'));
});

export const options = {
  scenarios: {
    coupon_direct: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERS,
      maxDuration: '60s',
    },
  },
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['avg<300', 'p(95)<500'],
  },
};

export default function () {
  const idx    = exec.scenario.iterationInTest % userIds.length;
  const userId = userIds[idx].userId;

  const res = http.post(
    `${BASE_URL}/api/v1/coupons/${COUPON_ID}/issue`,
    null,
    {
      headers: {
        'X-Gateway-Secret': GW_SECRET,
        'X-User-Id':        userId,
        'X-User-Role':      'USER',
        'Content-Type':     'application/json',
      },
    }
  );

  issueDuration.add(res.timings.duration);
  issueSuccessRate.add(res.status === 202 || res.status === 409);

  check(res, {
    '202 or 409': (r) => r.status === 202 || r.status === 409,
    'status < 500': (r) => r.status < 500,
  });
}
