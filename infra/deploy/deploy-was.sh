#!/usr/bin/env bash
set -euo pipefail

# EC2 2호기: gateway, eureka, config-server, application services
EDGE_IP=${EDGE_IP:-}
WAS_IP=${WAS_IP:-}
EDGE_HOST_IP=${EDGE_HOST_IP:-}
INFRA_HOST_IP=${INFRA_HOST_IP:-}
SSH_KEY=${SSH_KEY:-$HOME/.ssh/id_rsa}

GITHUB_OWNER=${GITHUB_OWNER:-}
GHCR_USERNAME=${GHCR_USERNAME:-}
GHCR_TOKEN=${GHCR_TOKEN:-}
GATEWAY_SECRET=${GATEWAY_SECRET:-}
JWT_SECRET=${JWT_SECRET:-}
ADMIN_SECRET=${ADMIN_SECRET:-}
KEYCLOAK_ADMIN=${KEYCLOAK_ADMIN:-}
KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-}
POSTGRES_USER=${POSTGRES_USER:-}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-}
TOSS_SECRET_KEY=${TOSS_SECRET_KEY:-}
SLACK_BOT_TOKEN=${SLACK_BOT_TOKEN:-}
SENTRY_DSN=${SENTRY_DSN:-}

required=(EDGE_IP WAS_IP EDGE_HOST_IP INFRA_HOST_IP GITHUB_OWNER GATEWAY_SECRET JWT_SECRET ADMIN_SECRET KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD POSTGRES_USER POSTGRES_PASSWORD SENTRY_DSN)
for name in "${required[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "ERROR: $name 환경변수는 필수입니다." >&2
    exit 1
  fi
done

SSH_OPTS=(-i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new)
PROXY_OPTS=(-o "ProxyCommand=ssh -i $SSH_KEY -o BatchMode=yes -o StrictHostKeyChecking=accept-new -W %h:%p ubuntu@$EDGE_IP")

ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" ubuntu@"$WAS_IP" "mkdir -p /home/ubuntu/omc"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" docker-compose.prod.was.yml ubuntu@"$WAS_IP":/home/ubuntu/omc/

for name in GITHUB_OWNER EDGE_HOST_IP INFRA_HOST_IP GATEWAY_SECRET JWT_SECRET ADMIN_SECRET \
  KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD POSTGRES_USER POSTGRES_PASSWORD TOSS_SECRET_KEY SLACK_BOT_TOKEN SENTRY_DSN; do
  printf -v "${name}_Q" '%q' "${!name}"
done
printf -v ghcr_user_q '%q' "$GHCR_USERNAME"
printf -v ghcr_token_q '%q' "$GHCR_TOKEN"

login_command=":"
if [ -n "$GHCR_TOKEN" ] && [ -n "$GHCR_USERNAME" ]; then
  login_command="printf '%s' $ghcr_token_q | docker login ghcr.io -u $ghcr_user_q --password-stdin"
fi

remote_command="cd /home/ubuntu/omc && \
  export GITHUB_OWNER=$GITHUB_OWNER_Q EDGE_HOST_IP=$EDGE_HOST_IP_Q INFRA_HOST_IP=$INFRA_HOST_IP_Q \
  GATEWAY_SECRET=$GATEWAY_SECRET_Q JWT_SECRET=$JWT_SECRET_Q ADMIN_SECRET=$ADMIN_SECRET_Q \
  KEYCLOAK_ADMIN=$KEYCLOAK_ADMIN_Q KEYCLOAK_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD_Q \
  POSTGRES_USER=$POSTGRES_USER_Q POSTGRES_PASSWORD=$POSTGRES_PASSWORD_Q \
  TOSS_SECRET_KEY=$TOSS_SECRET_KEY_Q SLACK_BOT_TOKEN=$SLACK_BOT_TOKEN_Q SENTRY_DSN=$SENTRY_DSN_Q && \
  $login_command && \
  docker compose -f docker-compose.prod.was.yml config --quiet && \
  docker compose -f docker-compose.prod.was.yml pull && \
  docker compose -f docker-compose.prod.was.yml up -d --remove-orphans"

echo "[WAS] 배포 시작: $WAS_IP (bastion: $EDGE_IP)"
ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" ubuntu@"$WAS_IP" "$remote_command"
echo "[WAS] 배포 완료"
