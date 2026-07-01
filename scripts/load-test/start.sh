#!/bin/bash
# 대량 트래픽 테스트 시작 전 실행
# Zipkin 샘플링을 10%로 낮춰 OOM 방지
#
# 실행:
#   bash scripts/load-test/start.sh
#
# 순서:
#   1. bash scripts/load-test/start.sh   # 샘플링 0.1로 서비스 재시작
#   2. JMeter 실행
#   3. bash scripts/load-test/stop.sh    # 샘플링 1.0으로 복원

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

echo "[load-test] Zipkin 샘플링 0.1로 설정 후 서비스 재시작..."

TRACING_SAMPLING_PROBABILITY=0.1 docker compose \
  -f "$PROJECT_ROOT/docker-compose.yml" \
  -f "$PROJECT_ROOT/docker-compose.services.yml" \
  up -d

echo "[load-test] 완료. JMeter 테스트를 시작하세요."
echo "[load-test] 테스트 종료 후 bash scripts/load-test/stop.sh 를 실행하세요."
