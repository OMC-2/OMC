#!/usr/bin/env bash
# 3계층 EC2 최초 파일 배치 스크립트. 컨테이너는 실행하지 않는다.
set -euo pipefail

PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
TERRAFORM_DIR="$PROJECT_ROOT/infra/terraform"
PEM_FILE=${PEM_FILE:-"$TERRAFORM_DIR/omc-key.pem"}
ENV_FILE=${ENV_FILE:-"$PROJECT_ROOT/.env.prod"}
EC2_USER=${EC2_USER:-ubuntu}
REMOTE_DIR=/home/$EC2_USER/omc

for command in terraform ssh scp; do
  command -v "$command" >/dev/null || { echo "ERROR: $command 명령이 필요합니다." >&2; exit 1; }
done
[ -f "$PEM_FILE" ] || { echo "ERROR: PEM 파일이 없습니다: $PEM_FILE" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "ERROR: 환경 파일이 없습니다: $ENV_FILE" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

TOSS_SECRET_KEY=${TOSS_SECRET_KEY:-test-secret-key}
if [ -z "${SENTRY_DSN:-}" ] && [ -f "$PROJECT_ROOT/.env" ]; then
  SENTRY_DSN=$(sed -n 's/^# SENTRY_DSN=//p' "$PROJECT_ROOT/.env" | head -n 1)
fi

required=(GITHUB_OWNER POSTGRES_USER POSTGRES_PASSWORD KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD GATEWAY_SECRET JWT_SECRET ADMIN_SECRET SLACK_BOT_TOKEN SENTRY_DSN)
for name in "${required[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "ERROR: $ENV_FILE 의 $name 값이 비어 있습니다." >&2
    exit 1
  fi
done

EDGE_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw edge_elastic_ip)
EDGE_PRIVATE_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw edge_private_ip)
WAS_PRIVATE_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw was_private_ip)
INFRA_PRIVATE_IP=$(terraform -chdir="$TERRAFORM_DIR" output -raw infra_private_ip)

SSH_OPTS=(-i "$PEM_FILE" -o BatchMode=yes -o StrictHostKeyChecking=accept-new)
PROXY_OPTS=(-o "ProxyCommand=ssh -i $PEM_FILE -o BatchMode=yes -o StrictHostKeyChecking=accept-new -W %h:%p $EC2_USER@$EDGE_IP")

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

dotenv_value() {
  local value=${1//\\/\\\\}
  value=${value//\'/\\\'}
  printf "'%s'" "$value"
}

write_env() {
  local file=$1
  shift
  : > "$file"
  while [ "$#" -gt 0 ]; do
    local name=$1
    local value=$2
    printf '%s=%s\n' "$name" "$(dotenv_value "$value")" >> "$file"
    shift 2
  done
  chmod 600 "$file"
}

write_env "$tmp_dir/edge.env" \
  WAS_HOST_IP "$WAS_PRIVATE_IP" \
  INFRA_HOST_IP "$INFRA_PRIVATE_IP" \
  KEYCLOAK_ADMIN "$KEYCLOAK_ADMIN" \
  KEYCLOAK_ADMIN_PASSWORD "$KEYCLOAK_ADMIN_PASSWORD" \
  POSTGRES_USER "$POSTGRES_USER" \
  POSTGRES_PASSWORD "$POSTGRES_PASSWORD"

write_env "$tmp_dir/was.env" \
  GITHUB_OWNER "$GITHUB_OWNER" \
  EDGE_HOST_IP "$EDGE_PRIVATE_IP" \
  INFRA_HOST_IP "$INFRA_PRIVATE_IP" \
  GATEWAY_SECRET "$GATEWAY_SECRET" \
  JWT_SECRET "$JWT_SECRET" \
  ADMIN_SECRET "$ADMIN_SECRET" \
  KEYCLOAK_ADMIN "$KEYCLOAK_ADMIN" \
  KEYCLOAK_ADMIN_PASSWORD "$KEYCLOAK_ADMIN_PASSWORD" \
  POSTGRES_USER "$POSTGRES_USER" \
  POSTGRES_PASSWORD "$POSTGRES_PASSWORD" \
  TOSS_SECRET_KEY "$TOSS_SECRET_KEY" \
  SLACK_BOT_TOKEN "$SLACK_BOT_TOKEN" \
  SENTRY_DSN "$SENTRY_DSN"

write_env "$tmp_dir/infra.env" \
  INFRA_HOST_IP "$INFRA_PRIVATE_IP" \
  WAS_HOST_IP "$WAS_PRIVATE_IP" \
  POSTGRES_USER "$POSTGRES_USER" \
  POSTGRES_PASSWORD "$POSTGRES_PASSWORD"

echo "[1/4] 서버 디렉토리 생성"
ssh "${SSH_OPTS[@]}" "$EC2_USER@$EDGE_IP" \
  "mkdir -p $REMOTE_DIR/docker/{nginx,keycloak,toss-wiremock}"
ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$EC2_USER@$WAS_PRIVATE_IP" \
  "mkdir -p $REMOTE_DIR"
ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$EC2_USER@$INFRA_PRIVATE_IP" \
  "mkdir -p $REMOTE_DIR/docker/{postgres/init,prometheus,loki,promtail,grafana}"

echo "[2/4] Edge 파일 및 환경 배치"
scp "${SSH_OPTS[@]}" "$PROJECT_ROOT/docker-compose.prod.edge.yml" "$EC2_USER@$EDGE_IP:$REMOTE_DIR/"
scp "${SSH_OPTS[@]}" "$PROJECT_ROOT/docker/nginx/default.prod.conf.template" "$EC2_USER@$EDGE_IP:$REMOTE_DIR/docker/nginx/"
scp "${SSH_OPTS[@]}" -r "$PROJECT_ROOT/docker/keycloak/." "$EC2_USER@$EDGE_IP:$REMOTE_DIR/docker/keycloak/"
scp "${SSH_OPTS[@]}" -r "$PROJECT_ROOT/docker/toss-wiremock/." "$EC2_USER@$EDGE_IP:$REMOTE_DIR/docker/toss-wiremock/"
scp "${SSH_OPTS[@]}" "$tmp_dir/edge.env" "$EC2_USER@$EDGE_IP:$REMOTE_DIR/.env.prod"

echo "[3/4] WAS 파일 및 환경 배치"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$PROJECT_ROOT/docker-compose.prod.was.yml" "$EC2_USER@$WAS_PRIVATE_IP:$REMOTE_DIR/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$tmp_dir/was.env" "$EC2_USER@$WAS_PRIVATE_IP:$REMOTE_DIR/.env.prod"

echo "[4/4] Infra 파일 및 환경 배치"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$PROJECT_ROOT/docker-compose.prod.infra.yml" "$EC2_USER@$INFRA_PRIVATE_IP:$REMOTE_DIR/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" -r "$PROJECT_ROOT/docker/postgres/init/." "$EC2_USER@$INFRA_PRIVATE_IP:$REMOTE_DIR/docker/postgres/init/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$PROJECT_ROOT/docker/prometheus/prometheus.prod.yml.template" "$EC2_USER@$INFRA_PRIVATE_IP:$REMOTE_DIR/docker/prometheus/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$PROJECT_ROOT/docker/loki/loki-config.yml" "$EC2_USER@$INFRA_PRIVATE_IP:$REMOTE_DIR/docker/loki/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$PROJECT_ROOT/docker/promtail/promtail-config.yml" "$EC2_USER@$INFRA_PRIVATE_IP:$REMOTE_DIR/docker/promtail/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" -r "$PROJECT_ROOT/docker/grafana/." "$EC2_USER@$INFRA_PRIVATE_IP:$REMOTE_DIR/docker/grafana/"
scp "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$tmp_dir/infra.env" "$EC2_USER@$INFRA_PRIVATE_IP:$REMOTE_DIR/.env.prod"

ssh "${SSH_OPTS[@]}" "$EC2_USER@$EDGE_IP" \
  "chmod 600 $REMOTE_DIR/.env.prod && cp $REMOTE_DIR/.env.prod $REMOTE_DIR/.env"
ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$EC2_USER@$WAS_PRIVATE_IP" \
  "chmod 600 $REMOTE_DIR/.env.prod && cp $REMOTE_DIR/.env.prod $REMOTE_DIR/.env"
ssh "${SSH_OPTS[@]}" "${PROXY_OPTS[@]}" "$EC2_USER@$INFRA_PRIVATE_IP" \
  "chmod 600 $REMOTE_DIR/.env.prod && cp $REMOTE_DIR/.env.prod $REMOTE_DIR/.env && sed 's/__WAS_HOST_IP__/$WAS_PRIVATE_IP/g' $REMOTE_DIR/docker/prometheus/prometheus.prod.yml.template > $REMOTE_DIR/docker/prometheus/prometheus.prod.yml"

echo "초기 파일 배치 완료. 컨테이너는 실행하지 않았습니다."
