#!/bin/bash
# 부하 테스트 전 Redis 상태 초기화
# 사용법: ./redis-reset.sh <dropId> [stock] [host] [port]
#   dropId: 테스트할 드롭 ID (필수)
#   stock:  초기 재고 수 (기본값: 100)
#   host:   Redis 호스트 (기본값: localhost)
#   port:   Redis 포트 (기본값: 6379)

DROP_ID=${1:?"dropId를 첫 번째 인자로 입력하세요. 예) ./redis-reset.sh <dropId>"}
STOCK=${2:-100}
HOST=${3:-localhost}
PORT=${4:-6379}

echo "▶ Redis 초기화 시작"
echo "  dropId : $DROP_ID"
echo "  stock  : $STOCK"
echo "  Redis  : $HOST:$PORT"
echo ""

redis-cli -h "$HOST" -p "$PORT" DEL \
  "stock:$DROP_ID" \
  "purchased:$DROP_ID" \
  "holds:$DROP_ID" \
  "queue:$DROP_ID"

redis-cli -h "$HOST" -p "$PORT" SET "stock:$DROP_ID" "$STOCK"
redis-cli -h "$HOST" -p "$PORT" SET "drop:$DROP_ID:status" "OPEN"

echo ""
echo "▶ 초기화 완료. 현재 상태:"
echo "  stock  : $(redis-cli -h "$HOST" -p "$PORT" GET "stock:$DROP_ID")"
echo "  status : $(redis-cli -h "$HOST" -p "$PORT" GET "drop:$DROP_ID:status")"
echo "  holds  : $(redis-cli -h "$HOST" -p "$PORT" ZCARD "holds:$DROP_ID")"
