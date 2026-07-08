#!/bin/bash
set -e

# ============================================================
# EC2 2호기 (Private Subnet) 배포 스크립트
# 역할: gateway, config-server, eureka-server 및 WAS Core 8개 기동
# 통신: 1호기(Edge)를 Bastion/ProxyJump 삼아 프라이빗 접속 수행
# ============================================================

EDGE_IP=${EDGE_IP:-""}       # 1호기 퍼블릭 IP (Bastion 경유지)
WAS_IP=${WAS_IP:-""}         # 2호기 프라이빗 IP (대상지)
SSH_KEY=${SSH_KEY:-"~/.ssh/id_rsa"}

# 프라이빗 망 환경 변수
EDGE_HOST_IP=${EDGE_HOST_IP:-""}   # 1호기 프라이빗 IP (Keycloak 타겟용)
INFRA_HOST_IP=${INFRA_HOST_IP:-""} # 3호기 프라이빗 IP (DB/Redis/Kafka 타겟용)

# 서비스 관련 보안 변수
GITHUB_OWNER=${GITHUB_OWNER:-"ro-dong-wan"}
GATEWAY_SECRET=${GATEWAY_SECRET:-"local-secret"}
JWT_SECRET=${JWT_SECRET:-"local-jwt-secret-key-32bytes-must-be-long"}
POSTGRES_USER=${POSTGRES_USER:-"omc"}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-"password"}
TOSS_SECRET_KEY=${TOSS_SECRET_KEY:-""}
SLACK_BOT_TOKEN=${SLACK_BOT_TOKEN:-""}

if [ -z "$EDGE_IP" ] || [ -z "$WAS_IP" ] || [ -z "$EDGE_HOST_IP" ] || [ -z "$INFRA_HOST_IP" ]; then
  echo "❌ 에러: EDGE_IP, WAS_IP, EDGE_HOST_IP, INFRA_HOST_IP 환경변수는 필수입니다."
  echo "사용법: EDGE_IP=1.1.1.1 WAS_IP=10.0.2.x EDGE_HOST_IP=10.0.1.y INFRA_HOST_IP=10.0.2.z ./deploy-was.sh"
  exit 1
fi

echo "🚀 [2호기 WAS] 원격 배포 프로세스 시작 (경유: $EDGE_IP ➔ 대상: $WAS_IP)..."

# SSH / SCP 공통 프록시 연결 인자 정의
PROXY_OPT="-o ProxyCommand=\"ssh -i $SSH_KEY -W %h:%p ubuntu@$EDGE_IP\""

# 1. 원격 디렉토리 생성
ssh -i "$SSH_KEY" -o ProxyCommand="ssh -i $SSH_KEY -W %h:%p ubuntu@$EDGE_IP" ubuntu@"$WAS_IP" "mkdir -p ~/omc"

# 2. 컴포즈 파일 전송 (Bastion 경유)
scp -i "$SSH_KEY" $PROXY_OPT docker-compose.prod.was.yml ubuntu@"$WAS_IP":~/omc/

# 3. 원격 컨테이너 기동
ssh -i "$SSH_KEY" -o ProxyCommand="ssh -i $SSH_KEY -W %h:%p ubuntu@$EDGE_IP" ubuntu@"$WAS_IP" "
  cd ~/omc && \
  export GITHUB_OWNER=$GITHUB_OWNER && \
  export EDGE_HOST_IP=$EDGE_HOST_IP && \
  export INFRA_HOST_IP=$INFRA_HOST_IP && \
  export GATEWAY_SECRET=$GATEWAY_SECRET && \
  export JWT_SECRET=$JWT_SECRET && \
  export POSTGRES_USER=$POSTGRES_USER && \
  export POSTGRES_PASSWORD=$POSTGRES_PASSWORD && \
  export TOSS_SECRET_KEY=$TOSS_SECRET_KEY && \
  export SLACK_BOT_TOKEN=$SLACK_BOT_TOKEN && \
  docker compose -f docker-compose.prod.was.yml pull && \
  docker compose -f docker-compose.prod.was.yml up -d --remove-orphans
"

echo "✅ [2호기 WAS] 배포 및 기동 명령 완료!"
