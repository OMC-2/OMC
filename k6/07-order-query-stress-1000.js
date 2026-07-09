import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
// order 주문 조회 극한 스트레스 (order 직접, 최대 1000 VU)
// 목적: order 조회의 진짜 한계점을 찾는다. 계단식으로 100→1000 VU까지 올리며
//       p95/성공률이 어디서 무너지는지, DB 커넥션풀(기본 10)이 병목이 되는지 관찰.
// 주의: 1000 VU는 k6 실행 PC 자원도 많이 쓴다. PC가 병목이면 order가 아닌 클라이언트
//       한계를 재게 되므로, 실행 중 PC CPU/메모리도 함께 관찰할 것.
//
// 실행:
//   k6 run -e ORDER_ID=xxx k6/07-order-query-stress-1000.js
const BASE = __ENV.ORDER_URL || 'http://localhost:8083';
const GATEWAY_SECRET = __ENV.GATEWAY_SECRET || 'local-secret';
const USER_ID = __ENV.USER_ID || '00000000-0000-0000-0000-000000000001';
const USER_ROLE = __ENV.USER_ROLE || 'USER';
const ORDER_ID = __ENV.ORDER_ID || 'CHANGE_ME';
// sleep을 짧게(0.1s) 해서 진짜 부하를 건다. 환경변수로 조절 가능.
const SLEEP = parseFloat(__ENV.SLEEP || '0.1');
const queryDuration = new Trend('order_query_duration');
const querySuccess = new Rate('order_query_success');
export const options = {
  // 계단식: 100 -> 300 -> 600 -> 1000 VU. 각 단계에서 p95/성공률 변화를 관찰해 한계점 파악
  stages: [
    { duration: '30s', target: 100 },
    { duration: '1m', target: 300 },
    { duration: '1m', target: 600 },
    { duration: '1m', target: 1000 },
    { duration: '1m', target: 1000 },   // 1000 유지 (지속 한계)
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 한계 탐색이라 관대하게 (넘어도 계속 실행)
    http_req_duration: ['p(95)<5000'],
    order_query_success: ['rate>0.80'],
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
  sleep(SLEEP);
}
