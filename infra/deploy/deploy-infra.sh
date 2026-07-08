#!/bin/bash
set -e

# ============================================================
# EC2 3호기 (Private Subnet) 배포 스크립트
# 역할: postgres, redis, kafka, zookeeper 및 모니터링 인프라 기동
# 통신: 1호기(Edge)를 Bastion/ProxyJump 삼아 프라이빗 접속 수행
# ============================================================

EDGE_IP=${EDGE_IP:-""}       # 1호기 퍼블릭 IP (Bastion 경유지)
INFRA_IP=${INFRA_IP:-""}     # 3호기 프라이빗 IP (대상지)
SSH_KEY=${SSH_KEY:-"~/.ssh/id_rsa"}

# 프라이빗 망 환경 변수 (3호기 자신의 IP를 주입)
INFRA_HOST_IP=${INFRA_HOST_IP:-""}

# DB 보안 변수
POSTGRES_USER=${POSTGRES_USER:-"omc"}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-"password"}

if [ -z "$EDGE_IP" ] || [ -z "$INFRA_IP" ] || [ -z "$INFRA_HOST_IP" ]; then
  echo "❌ 에러: EDGE_IP, INFRA_IP, INFRA_HOST_IP 환경변수는 필수입니다."
  echo "사용법: EDGE_IP=1.1.1.1 INFRA_IP=10.0.2.z INFRA_HOST_IP=10.0.2.z ./deploy-infra.sh"
  exit 1
fi

echo "🚀 [3호기 Infra] 원격 배포 프로세스 시작 (경유: $EDGE_IP ➔ 대상: $INFRA_IP)..."

# SSH / SCP 공통 프록시 연결 인자 정의
PROXY_OPT="-o ProxyCommand=\"ssh -i $SSH_KEY -W %h:%p ubuntu@$EDGE_IP\""

# 1. 원격 디렉토리 생성
ssh -i "$SSH_KEY" -o ProxyCommand="ssh -i $SSH_KEY -W %h:%p ubuntu@$EDGE_IP" ubuntu@"$INFRA_IP" \
  "mkdir -p ~/omc/docker/postgres/init ~/omc/docker/prometheus ~/omc/docker/loki ~/omc/docker/promtail ~/omc/docker/grafana"

# 2. 로컬 인프라 설정 파일 전송 (Bastion 경유)
scp -i "$SSH_KEY" $PROXY_OPT -r docker/postgres/init/* ubuntu@"$INFRA_IP":~/omc/docker/postgres/init/
scp -i "$SSH_KEY" $PROXY_OPT docker/prometheus/prometheus.yml ubuntu@"$INFRA_IP":~/omc/docker/prometheus/
scp -i "$SSH_KEY" $PROXY_OPT docker/loki/loki-config.yml ubuntu@"$INFRA_IP":~/omc/docker/loki/
scp -i "$SSH_KEY" $PROXY_OPT docker/promtail/promtail-config.yml ubuntu@"$INFRA_IP":~/omc/docker/promtail/
scp -i "$SSH_KEY" $PROXY_OPT docker-compose.prod.infra.yml ubuntu@"$INFRA_IP":~/omc/

# 3. 원격 컨테이너 기동
ssh -i "$SSH_KEY" -o ProxyCommand="ssh -i $SSH_KEY -W %h:%p ubuntu@$EDGE_IP" ubuntu@"$INFRA_IP" "
  cd ~/omc && \
  export INFRA_HOST_IP=$INFRA_HOST_IP && \
  export POSTGRES_USER=$POSTGRES_USER && \
  export POSTGRES_PASSWORD=$POSTGRES_PASSWORD && \
  docker compose -f docker-compose.prod.infra.yml pull && \
  docker compose -f docker-compose.prod.infra.yml up -d --remove-orphans
"

echo "✅ [3호기 Infra] 배포 및 기동 명령 완료!"
