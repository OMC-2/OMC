#!/bin/bash
# Docker 전체 인프라 + 서비스 기동 스크립트
#
# ================================================================
# 사용법
# ================================================================
#
#   bash docker-up.sh          # 전체 기동 (빌드 + 인프라 + 서비스)
#   bash docker-up.sh infra    # 인프라만 기동 (postgres, redis, kafka, keycloak)
#   bash docker-up.sh services # 빌드 후 서비스만 기동 (eureka, config, gateway, user-service, drop-service)
#
# ================================================================
# 기동 순서
# ================================================================
#
#   1단계: Gradle bootJar 빌드
#     각 서비스의 FAT JAR을 빌드한다 (Docker 이미지가 JAR을 복사하는 방식)
#
#   2단계: Docker 이미지 빌드
#     변경된 소스 코드를 반영하여 이미지를 새로 빌드한다
#
#   3단계: 인프라 기동
#     postgres / redis / zookeeper / kafka / kafka-ui / keycloak
#
#   4단계: Kafka + Keycloak healthy 대기
#     → gateway가 Keycloak JWKS URI를 참조하므로 먼저 헬시해야 함
#
#   5단계: 서비스 기동
#     eureka-server → config-server → gateway + user-service + drop-service
#
#   6단계: gateway + user-service + drop-service healthy 대기
#     → E2E 테스트 실행 가능 상태 확인
#
# ================================================================
# 사전 조건
# ================================================================
#
#   - Docker Desktop이 실행 중이어야 한다
#   - 프로젝트 루트(docker-compose.yml이 있는 위치)에서 실행한다
#
# ================================================================

set -e

COMPOSE_INFRA="docker-compose.yml"
COMPOSE_SERVICES="docker-compose.services.yml"

# ----------------------------------------------------------------
# 헬스체크 대기 함수
#   $1: 컨테이너 이름
#   $2: 타임아웃(초), 기본값 120
# ----------------------------------------------------------------
wait_healthy() {
  local container=$1
  local timeout=${2:-120}
  local elapsed=0

  echo "  ⏳ $container 준비 대기 중..."
  until docker inspect "$container" --format '{{.State.Health.Status}}' 2>/dev/null | grep -q "^healthy$"; do
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "  ❌ $container 헬스체크 타임아웃 (${timeout}s 초과)"
      exit 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  echo "  ✅ $container 준비 완료"
}

# ----------------------------------------------------------------
# 1단계: Gradle bootJar 빌드 + Docker 이미지 빌드
#   Docker 이미지가 build/libs/*.jar을 복사하는 방식이므로
#   이미지 빌드 전에 반드시 JAR을 먼저 생성해야 한다
# ----------------------------------------------------------------
build_services() {
  echo ""
  echo "▶ [1단계] Gradle bootJar 빌드"
  ./gradlew :services:gateway:bootJar \
            :services:user-service:bootJar \
            :services:drop-service:bootJar \
            :services:eureka-server:bootJar \
            :services:config-server:bootJar

  echo ""
  echo "▶ [2단계] Docker 이미지 빌드"
  docker compose -f "$COMPOSE_INFRA" -f "$COMPOSE_SERVICES" build \
    eureka-server config-server gateway user-service drop-service
}

# ----------------------------------------------------------------
# 3단계: 인프라 기동
# ----------------------------------------------------------------
start_infra() {
  echo ""
  echo "▶ [3단계] 인프라 기동 (postgres / redis / kafka / keycloak)"
  docker compose -f "$COMPOSE_INFRA" up -d

  echo ""
  echo "▶ [4단계] Kafka + Keycloak healthy 대기 (최대 180초)"
  # kafka는 zookeeper 재시작 후 NodeExists 에러가 발생할 수 있으므로 healthy 확인
  wait_healthy omc-kafka 180
  wait_healthy omc-keycloak 180
}

# ----------------------------------------------------------------
# 5단계: 서비스 기동
# ----------------------------------------------------------------
start_services() {
  echo ""
  echo "▶ [5단계] 서비스 기동 (eureka / config-server / gateway / user-service / drop-service)"
  docker compose -f "$COMPOSE_INFRA" -f "$COMPOSE_SERVICES" up -d \
    eureka-server config-server gateway user-service drop-service

  echo ""
  echo "▶ [6단계] gateway + user-service + drop-service healthy 대기 (최대 180초)"
  wait_healthy omc-gateway 180
  wait_healthy omc-user-service 180
  wait_healthy omc-drop-service 180
}

# ----------------------------------------------------------------
# 실행 분기
# ----------------------------------------------------------------
TARGET=${1:-all}

case "$TARGET" in
  infra)
    start_infra
    ;;
  services)
    build_services
    start_services
    ;;
  all|"")
    build_services
    start_infra
    start_services
    ;;
  *)
    echo "알 수 없는 대상: $TARGET"
    echo "사용법: bash docker-up.sh [infra|services|all]"
    exit 1
    ;;
esac

# ----------------------------------------------------------------
# 최종 상태 출력
# ----------------------------------------------------------------
echo ""
echo "▶ 기동 완료 — 컨테이너 상태"
docker compose -f "$COMPOSE_INFRA" -f "$COMPOSE_SERVICES" ps \
  --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null
echo ""
echo "✅ E2E 테스트 실행 가능: bash e2e/run.sh [대상]"
