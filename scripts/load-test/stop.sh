#!/bin/bash
# 대량 트래픽 테스트 종료 후 실행
# Zipkin 샘플링을 기본값(100%)으로 복원

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

echo "[load-test] Zipkin 샘플링 1.0으로 복원 후 서비스 재시작..."

docker compose \
  -f "$PROJECT_ROOT/docker-compose.yml" \
  -f "$PROJECT_ROOT/docker-compose.services.yml" \
  up -d

echo "[load-test] 완료. 샘플링이 기본값(100%)으로 복원됐습니다."
