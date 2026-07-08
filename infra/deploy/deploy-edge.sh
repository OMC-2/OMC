#!/bin/bash
set -e

# ============================================================
# EC2 1호기 (Public Subnet) 배포 스크립트
# 역할: nginx, toss-wiremock, keycloak 기동
# ============================================================

EDGE_IP=${EDGE_IP:-""}
SSH_KEY=${SSH_KEY:-"~/.ssh/id_rsa"}

# 프라이빗 주소 매핑 (3호기 IP가 필요)
INFRA_HOST_IP=${INFRA_HOST_IP:-""}

# Keycloak 및 Postgres 환경변수
KEYCLOAK_ADMIN=${KEYCLOAK_ADMIN:-"admin"}
KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-"admin"}
POSTGRES_USER=${POSTGRES_USER:-"omc"}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-"password"}

if [ -z "$EDGE_IP" ] || [ -z "$INFRA_HOST_IP" ]; then
  echo "❌ 에러: EDGE_IP 및 INFRA_HOST_IP 환경변수는 필수입니다."
  echo "사용법: EDGE_IP=x.x.x.x INFRA_HOST_IP=y.y.y.y ./deploy-edge.sh"
  exit 1
fi

echo "🚀 [1호기 Edge] 원격 배포 프로세스 시작 (호스트: $EDGE_IP)..."

# 1. 원격 디렉토리 생성
ssh -i "$SSH_KEY" ubuntu@"$EDGE_IP" "mkdir -p ~/omc/docker/nginx ~/omc/docker/keycloak"

# 2. 로컬 설정 파일 및 컴포즈 파일 전송
scp -i "$SSH_KEY" -r docker/keycloak/* ubuntu@"$EDGE_IP":~/omc/docker/keycloak/
scp -i "$SSH_KEY" docker/nginx/default.conf ubuntu@"$EDGE_IP":~/omc/docker/nginx/
scp -i "$SSH_KEY" docker-compose.prod.edge.yml ubuntu@"$EDGE_IP":~/omc/

# 3. 원격 컨테이너 기동
ssh -i "$SSH_KEY" ubuntu@"$EDGE_IP" "
  cd ~/omc && \
  export INFRA_HOST_IP=$INFRA_HOST_IP && \
  export KEYCLOAK_ADMIN=$KEYCLOAK_ADMIN && \
  export KEYCLOAK_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD && \
  export POSTGRES_USER=$POSTGRES_USER && \
  export POSTGRES_PASSWORD=$POSTGRES_PASSWORD && \
  docker compose -f docker-compose.prod.edge.yml pull && \
  docker compose -f docker-compose.prod.edge.yml up -d --remove-orphans
"

echo "✅ [1호기 Edge] 배포 및 기동 명령 완료!"
