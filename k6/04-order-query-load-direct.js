import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
// order 주문 조회 API 부하 테스트 (order 직접 호출 - gateway RateLimiter 우회)
// gateway(8080) 경유 시 RedisRateLimiter(10/s)에 막히므로, order-service(8083)를 직접 호출해
// 순수 order 성능을 측정한다. 인증은 gateway가 넣어주는 3개 헤더를 수동으로 주입한다.
//   (order는 GatewayHeaderAuthFilter로 헤더 인증만 하고, 조회는 orderId만 받음)
//
// 실행:
//   k6 run -e ORDER_ID=xxx k6/04-order-query-load-direct.js
const BASE = __ENV.ORDER_URL || 'http://localhost:8083';   // order 직접
const GATEWAY_SECRET = __ENV.GATEWAY_SECRET || 'local-secret';
const USER_ID = __ENV.USER_ID || '00000000-0000-0000-0000-000000000001';
const USER_ROLE = __ENV.USER_ROLE || 'USER';
const ORDER_ID = __ENV.ORDER_ID || 'CHANGE_ME';
const queryDuration = new Trend('order_query_duration');
const querySuccess = new Rate('order_query_success');
export const options = {
  // 단계적 부하: 워밍업 -> 증가 -> 유지 -> 감소
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 50 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    order_query_success: ['rate>0.95'],
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
