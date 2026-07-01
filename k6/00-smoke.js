import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 작동 검증용 스모크 테스트 (인증 불필요)
// order의 actuator/health를 가볍게 때려서 k6 -> order 경로가 도는지 확인
export const options = {
  vus: 5,           // 가상 사용자 5명
  duration: '30s',  // 30초간
};

const BASE = __ENV.ORDER_URL || 'http://localhost:8083';

export default function () {
  const res = http.get(`${BASE}/actuator/health`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body has UP': (r) => r.body.includes('UP'),
  });
  sleep(1);
}
