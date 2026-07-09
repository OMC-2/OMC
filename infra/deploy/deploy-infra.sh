#!/usr/bin/env bash
set -euo pipefail

# EC2 3호기: postgres, redis, kafka, monitoring
EDGE_IP=${EDGE_IP:-}
INFRA_IP=${INFRA_IP:-}
INFRA_HOST_IP=${INFRA_HOST_IP:-}
WAS_HOST_IP=${WAS_HOST_IP:-${WAS_IP:-}}
SSH_KEY=${SSH_KEY:-$HOME/.ssh/id_rsa}
POSTGRES_USER=${POSTGRES_USER:-}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-}

required=(EDGE_IP INFRA_IP INFRA_HOST_IP WAS_HOST_IP POSTGRES_USER POSTGRES_PASSWORD)
for name in "${required[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "ERROR: $name 환경변수는 필수입니다." >&2
    exit 1
  fi
done

SSH_OPTS=(-i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new)
PROXY_OPTS=(-o "ProxyCommand=ssh -i $SSH_KEY -o BatchMode=yes -o StrictHostKeyChecking=accept-new -W %h:%p ubuntu@$EDGE_IP")
target=/home/ubuntu/omc

ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" ubuntu@"$INFRA_IP" \
  "mkdir -p $target/docker/postgres/init $target/docker/prometheus $target/docker/loki $target/docker/promtail $target/docker/grafana"

scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" docker-compose.prod.infra.yml ubuntu@"$INFRA_IP":"$target/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" -r docker/postgres/init/. ubuntu@"$INFRA_IP":"$target/docker/postgres/init/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" docker/prometheus/prometheus.prod.yml.template ubuntu@"$INFRA_IP":"$target/docker/prometheus/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" docker/loki/loki-config.yml ubuntu@"$INFRA_IP":"$target/docker/loki/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" docker/promtail/promtail-config.yml ubuntu@"$INFRA_IP":"$target/docker/promtail/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" -r docker/grafana/. ubuntu@"$INFRA_IP":"$target/docker/grafana/"

printf -v infra_q '%q' "$INFRA_HOST_IP"
printf -v was_q '%q' "$WAS_HOST_IP"
printf -v pg_user_q '%q' "$POSTGRES_USER"
printf -v pg_password_q '%q' "$POSTGRES_PASSWORD"

remote_command="cd $target && \
  sed 's/__WAS_HOST_IP__/$was_q/g' docker/prometheus/prometheus.prod.yml.template > docker/prometheus/prometheus.prod.yml && \
  export INFRA_HOST_IP=$infra_q POSTGRES_USER=$pg_user_q POSTGRES_PASSWORD=$pg_password_q && \
  docker compose -f docker-compose.prod.infra.yml config --quiet && \
  docker compose -f docker-compose.prod.infra.yml pull && \
  docker compose -f docker-compose.prod.infra.yml up -d --remove-orphans"

echo "[Infra] 배포 시작: $INFRA_IP (bastion: $EDGE_IP)"
ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" ubuntu@"$INFRA_IP" "$remote_command"
echo "[Infra] 배포 완료"
