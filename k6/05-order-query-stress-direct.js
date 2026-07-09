import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
// order 주문 조회 스트레스 테스트 (order 직접 호출 - gateway RateLimiter 우회)
// 목적: 부하를 계단식으로 한계까지 올려 order의 성능 한계점을 관찰
//
// 실행:
//   k6 run -e ORDER_ID=xxx k6/05-order-query-stress-direct.js
const BASE = __ENV.ORDER_URL || 'http://localhost:8083';
const GATEWAY_SECRET = __ENV.GATEWAY_SECRET || 'local-secret';
const USER_ID = __ENV.USER_ID || '00000000-0000-0000-0000-000000000001';
const USER_ROLE = __ENV.USER_ROLE || 'USER';
const ORDER_ID = __ENV.ORDER_ID || 'CHANGE_ME';
const queryDuration = new Trend('order_query_duration');
const querySuccess = new Rate('order_query_success');
export const options = {
  // 계단식 스트레스: 50 -> 100 -> 200 -> 300 VU
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '1m', target: 200 },
    { duration: '1m', target: 300 },
    { duration: '1m', target: 300 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    order_query_success: ['rate>0.90'],
  },
};
const params = {
  headers: {
    'X-Gateway-Secret': GATEWAY_SECRET,
    'X-User-Id': USER_ID,
    'X-User-Role': USER_ROLE,
  },
};
export default function () {
  const res = http.get(`${BASE}/api/v1/orders/${ORDER_ID}`, params);
  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has orderId': (r) => r.json('data.orderId') !== undefined,
  });
  queryDuration.add(res.timings.duration);
  querySuccess.add(ok);
  sleep(0.5);
}
