#!/bin/bash
# Docker 전체 인프라 + 서비스 기동 스크립트
#
# ================================================================
# 사용법
# ================================================================
#
#   bash docker-up.sh          # 전체 기동 (빌드 + 인프라 + 서비스)
#   bash docker-up.sh infra    # 인프라만 기동 (postgres, redis, kafka, keycloak)
#   bash docker-up.sh services # 빌드 후 애플리케이션 서비스 기동
#
# ================================================================
# 기동 순서
# ================================================================
#
#   0단계: 기존 컨테이너 전체 종료 (down --remove-orphans)
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
#     eureka-server → config-server → gateway + user/drop/product/order/payment/coupon/notification
#
#   6단계: 서비스 healthy 대기
#     → actuator/health 기준으로 컨테이너가 정상 기동되었는지 확인
#
#   7단계: Gateway 라우팅 확인
#     → Eureka 전파가 완료되어 Gateway가 실제로 라우팅 가능한지 확인
#     → 서비스가 healthy여도 Eureka 캐시 갱신 전에는 503이 발생할 수 있음
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
# 0단계: 기존 컨테이너 전체 종료
# ----------------------------------------------------------------
echo "▶ [0단계] 기존 컨테이너 종료"
docker compose -f "$COMPOSE_INFRA" -f "$COMPOSE_SERVICES" down --remove-orphans

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
# Gateway 라우팅 대기 함수
#   Gateway의 Eureka 클라이언트 캐시 갱신을 기다려
#   실제 라우팅이 가능한 상태가 될 때까지 폴링한다
#   (서비스가 healthy여도 503 Service Unavailable이 날 수 있음)
#
#   $1: 확인할 URL (non-503 응답이 오면 라우팅 성공으로 간주)
#   $2: 표시 이름
#   $3: 타임아웃(초), 기본값 90
# ----------------------------------------------------------------
wait_gateway_routing() {
  local url=$1
  local label=$2
  local timeout=${3:-90}
  local elapsed=0

  echo "  ⏳ Gateway → $label 라우팅 대기 중 (Eureka 전파)..."
  while true; do
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
      -H "Content-Type: application/json" \
      -H "X-Gateway-Secret: local-secret" \
      -d '{}' \
      "$url" 2>/dev/null)
    if [ "$code" != "503" ] && [ -n "$code" ]; then
      echo "  ✅ Gateway → $label 라우팅 확인 (응답: $code)"
      break
    fi
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "  ❌ Gateway → $label 라우팅 타임아웃 (${timeout}s 초과, 마지막 응답: $code)"
      exit 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
}

# ----------------------------------------------------------------
# Eureka 등록 확인 함수
#   Eureka REST API로 서비스가 UP 상태로 등록되었는지 확인
#   (Eureka는 서비스명을 대문자로 저장하므로 대문자로 전달)
#
#   $1: Eureka 서비스명 (대문자, 예: COUPON-SERVICE)
#   $2: 타임아웃(초), 기본값 90
# ----------------------------------------------------------------
wait_eureka_registered() {
  local service=$1
  local timeout=${2:-90}
  local elapsed=0

  echo "  ⏳ Eureka → $service 등록 확인 중..."
  while true; do
    if curl -s "http://localhost:8761/eureka/apps/$service" 2>/dev/null | grep -q "UP"; then
      echo "  ✅ Eureka → $service 등록 확인"
      break
    fi
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "  ❌ Eureka → $service 등록 타임아웃 (${timeout}s 초과)"
      exit 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
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
            :services:product-service:bootJar \
            :services:payment-service:bootJar \
            :services:coupon-service:bootJar \
            :services:notification-service:bootJar \
            :services:eureka-server:bootJar \
            :services:order-service:bootJar \
            :services:raffle-service:bootJar \
            :services:config-server:bootJar

  echo ""
  echo "▶ [2단계] Docker 이미지 빌드"
  docker compose -f "$COMPOSE_INFRA" -f "$COMPOSE_SERVICES" build \
    eureka-server config-server gateway user-service drop-service product-service payment-service coupon-service notification-service order-service raffle-service
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
  echo "▶ [5단계] 서비스 기동 (eureka / config-server / gateway / user-service / drop-service / product-service / payment-service / coupon-service / notification-service / order-service / raffle-service)"
  docker compose -f "$COMPOSE_INFRA" -f "$COMPOSE_SERVICES" up -d \
    eureka-server config-server gateway user-service drop-service product-service payment-service coupon-service notification-service order-service raffle-service

  echo ""
  echo "▶ [6단계] 서비스 healthy 대기 (최대 500초, 병렬)"
  _pids=()
  wait_healthy omc-gateway 500 & _pids+=($!)
  wait_healthy omc-user-service 500 & _pids+=($!)
  wait_healthy omc-drop-service 500 & _pids+=($!)
  wait_healthy omc-product-service 500 & _pids+=($!)
  wait_healthy omc-payment-service 500 & _pids+=($!)
  wait_healthy omc-coupon-service 500 & _pids+=($!)
  wait_healthy omc-notification-service 500 & _pids+=($!)
  wait_healthy omc-order-service 500 & _pids+=($!)
  wait_healthy omc-raffle-service 500 & _pids+=($!)

  _failed=0
  for _pid in "${_pids[@]}"; do
    wait "$_pid" || _failed=1
  done
  [ "$_failed" -eq 0 ] || exit 1

  echo ""
  echo "▶ [7단계] Gateway 라우팅 확인 (Eureka 전파 대기, 최대 90초)"
  # user-service: permitAll 경로로 실제 라우팅 확인
  wait_gateway_routing "http://localhost:8080/api/v1/users/signup" "user-service" 90
  # drop-service: Eureka 등록 확인
  wait_eureka_registered "DROP-SERVICE" 90
  # coupon-service: Eureka 등록 확인
  wait_eureka_registered "COUPON-SERVICE" 90
  # notification-service: Eureka 등록 확인
  wait_eureka_registered "NOTIFICATION-SERVICE" 90
  # product-service: Eureka 등록 확인
  wait_eureka_registered "PRODUCT-SERVICE" 90
  # payment-service: Eureka 등록 확인
  wait_eureka_registered "PAYMENT-SERVICE" 90
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
