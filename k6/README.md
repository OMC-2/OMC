# k6 부하 테스트

## 사전 준비

```bash
# k6 설치 (macOS)
brew install k6

# 서비스 실행 확인
docker compose up -d
```

---

## 쿠폰 동시 발급 테스트

### 원커맨드 실행 (권장)

```bash
./k6/run.sh coupon <태그>
```

태그는 결과 폴더명에 포함돼 나중에 비교할 때 쓴다.

```bash
./k6/run.sh coupon pool-size-10   # 개선 전 (HikariCP 기본값)
./k6/run.sh coupon pool-size-50   # 개선 후 (커넥션 풀 확장)
```

### 자동 처리 순서

```
1. users.json 확인
   → 1000명 이상이면 스킵
   → 없거나 부족하면 generator.js 자동 실행 (약 7분 소요)

2. 단계별 실행 (각 단계마다 새 쿠폰 생성 → k6 실행 → DB/Redis 검증)
   smoke → load → stress-200 → stress-400 → stress-600 → stress-800 → stress-1000 → spike

3. 결과 저장
   k6/results/<날짜>_<태그>/<단계>/
     ├── summary.txt  (p95, 에러율, DB건수, Redis stock 요약)
     └── raw.json     (k6 원본 메트릭)

4. cleanup 여부 확인 (DB/Grafana 검증 완료 후 실행)
```

> Smoke / Load Test 임계값 초과 시 자동 중단  
> Stress Test 임계값 초과 시 "다음 레벨 계속할까요?" 확인 후 진행

---

## 테스트 단계 요약

| 단계 | VU | 쿠폰 수량 | 목적 |
|---|---|---|---|
| smoke | 5명 × 1회 | 100 | 스크립트/인증/라우팅 확인 |
| load | 0→100명 (ramp-up) | 100 | 정상 부하 응답시간 측정 |
| stress-200 | 200명 | 10000 | 한계치 탐색 시작 |
| stress-400 | 400명 | 10000 | 한계치 탐색 |
| stress-600 | 600명 | 10000 | 한계치 탐색 |
| stress-800 | 800명 | 10000 | 한계치 탐색 |
| stress-1000 | 1000명 | 10000 | SA 문서 기준 최대치 |
| spike | 0→200명 (5초) | 100 | 순간 트래픽 대응 확인 |

---

## 결과 폴더 구조

```
k6/results/
├── 2026-07-02_pool-size-10/   ← 개선 전
│   ├── smoke/
│   │   ├── summary.txt
│   │   └── raw.json
│   ├── load/
│   ├── stress-200/
│   └── ...
└── 2026-07-02_pool-size-50/   ← 개선 후 (비교용)
    └── ...
```

### summary.txt 예시

```
테스트: stress-200
태그: pool-size-10
날짜: 2026-07-02 15:32:11
쿠폰 ID: f47ac10b-58cc-4372-a567-0e02b2c3d479
totalQuantity: 10000
---
DB 발급 건수: 정상 (198건)
Redis stock: 정상 (stock: 9802)
```

---

## 수동 실행 (단계별 직접)

```bash
# 유저 생성 (users.json 없을 때)
COUNT=1000 node k6/generator.js

# 각 단계 개별 실행
k6 run --env SCENARIO=smoke  --env COUPON_ID=<uuid> k6/03-coupon-issue.js
k6 run --env SCENARIO=load   --env COUPON_ID=<uuid> k6/03-coupon-issue.js
k6 run --env SCENARIO=stress --env VUS=200  --env COUPON_ID=<uuid> k6/03-coupon-issue.js
k6 run --env SCENARIO=stress --env VUS=400  --env COUPON_ID=<uuid> k6/03-coupon-issue.js
k6 run --env SCENARIO=stress --env VUS=600  --env COUPON_ID=<uuid> k6/03-coupon-issue.js
k6 run --env SCENARIO=stress --env VUS=800  --env COUPON_ID=<uuid> k6/03-coupon-issue.js
k6 run --env SCENARIO=stress --env VUS=1000 --env COUPON_ID=<uuid> k6/03-coupon-issue.js
k6 run --env SCENARIO=spike  --env COUPON_ID=<uuid> k6/03-coupon-issue.js

# 테스트 유저 정리 (검증 완료 후)
node k6/cleanup.js
```

---

## 다른 팀원 스크립트

| 파일 | 설명 | 실행 예시 |
|---|---|---|
| `generator.js` | 테스트 유저 1000명 생성 | `COUNT=1000 node k6/generator.js` |
| `cleanup.js` | 테스트 유저 삭제 | `node k6/cleanup.js` |
| `00-smoke.js` | order-service 헬스체크 | `k6 run k6/00-smoke.js` |
| `01-order-query.js` | order 조회 부하테스트 | `k6 run -e ORDER_ID=xxx k6/01-order-query.js` |
| `02-product-inventory.js` | product/drop 부하테스트 | `k6 run -e SCENARIO=purchase -e DROP_ID=xxx k6/02-product-inventory.js` |

---

## 주의사항

- `cleanup.js`는 DB/Grafana 검증을 완전히 마친 뒤 실행한다. 데이터가 지워지면 실패 원인 추적이 불가능하다.
- `users.json`은 `.gitignore` 등록 — Git에 올리지 않는다.
- Stress Test는 에러가 나도 중단하지 않는 것이 기본이다. 서버 포화점을 찾는 게 목적이므로 에러가 나는 구간이 핵심 데이터다.
