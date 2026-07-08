#!/usr/bin/env bash
set -euo pipefail

# EC2 1호기: nginx, toss-wiremock, keycloak
EDGE_IP=${EDGE_IP:-}
WAS_HOST_IP=${WAS_HOST_IP:-${WAS_IP:-}}
INFRA_HOST_IP=${INFRA_HOST_IP:-}
SSH_KEY=${SSH_KEY:-$HOME/.ssh/id_rsa}
DEPLOY_EDGE_LOCAL=${DEPLOY_EDGE_LOCAL:-false}

KEYCLOAK_ADMIN=${KEYCLOAK_ADMIN:-}
KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-}
POSTGRES_USER=${POSTGRES_USER:-}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-}

required=(EDGE_IP WAS_HOST_IP INFRA_HOST_IP KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD POSTGRES_USER POSTGRES_PASSWORD)
for name in "${required[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "ERROR: $name 환경변수는 필수입니다." >&2
    exit 1
  fi
done

SSH_OPTS=(-i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new)

if [ "$DEPLOY_EDGE_LOCAL" = "true" ]; then
  target_root="$PWD"
else
  target_root="/home/ubuntu/omc"
  ssh "${SSH_OPTS[@]}" ubuntu@"$EDGE_IP" \
    "mkdir -p $target_root/docker/nginx $target_root/docker/keycloak $target_root/docker/toss-wiremock"
  scp "${SSH_OPTS[@]}" docker-compose.prod.edge.yml ubuntu@"$EDGE_IP":"$target_root/"
  scp "${SSH_OPTS[@]}" docker/nginx/default.prod.conf.template ubuntu@"$EDGE_IP":"$target_root/docker/nginx/"
  scp "${SSH_OPTS[@]}" -r docker/keycloak/. ubuntu@"$EDGE_IP":"$target_root/docker/keycloak/"
  scp "${SSH_OPTS[@]}" -r docker/toss-wiremock/. ubuntu@"$EDGE_IP":"$target_root/docker/toss-wiremock/"
fi

printf -v was_q '%q' "$WAS_HOST_IP"
printf -v infra_q '%q' "$INFRA_HOST_IP"
printf -v kc_admin_q '%q' "$KEYCLOAK_ADMIN"
printf -v kc_password_q '%q' "$KEYCLOAK_ADMIN_PASSWORD"
printf -v pg_user_q '%q' "$POSTGRES_USER"
printf -v pg_password_q '%q' "$POSTGRES_PASSWORD"

deploy_command="cd $target_root && \
  export WAS_HOST_IP=$was_q INFRA_HOST_IP=$infra_q \
  KEYCLOAK_ADMIN=$kc_admin_q KEYCLOAK_ADMIN_PASSWORD=$kc_password_q \
  POSTGRES_USER=$pg_user_q POSTGRES_PASSWORD=$pg_password_q && \
  docker compose -f docker-compose.prod.edge.yml config --quiet && \
  docker compose -f docker-compose.prod.edge.yml pull && \
  docker compose -f docker-compose.prod.edge.yml up -d --remove-orphans"

echo "[Edge] 배포 시작: $EDGE_IP"
if [ "$DEPLOY_EDGE_LOCAL" = "true" ]; then
  bash -c "$deploy_command"
else
  ssh "${SSH_OPTS[@]}" ubuntu@"$EDGE_IP" "$deploy_command"
fi
echo "[Edge] 배포 완료"
