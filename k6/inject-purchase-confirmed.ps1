# purchase.confirmed 이벤트를 Kafka에 대량 주입
# order가 소비 -> product 조회 -> 주문 생성 -> 아웃박스(order.created) 발행
# => Grafana "아웃박스 발행 처리량/지연" 패널이 살아 움직임
#
# 사용법:
#   .\k6\inject-purchase-confirmed.ps1 -Count 200
#   .\k6\inject-purchase-confirmed.ps1 -Count 200 -ProductId "다른상품ID"

param(
    [int]$Count = 100,
    [string]$ProductId = "019f13be-603c-75cd-b64f-0545abe60dd4",
    [string]$Topic = "purchase.confirmed",
    [string]$Container = "omc-kafka",
    [string]$Broker = "localhost:29092"
)

Write-Host "[주입 시작] $Count 건의 purchase.confirmed 이벤트를 $Topic 으로 발행합니다..." -ForegroundColor Cyan

# holdExpiresAt: 현재로부터 10분 후 (LocalDateTime 형식: yyyy-MM-ddTHH:mm:ss)
$holdExpires = (Get-Date).AddMinutes(10).ToString("yyyy-MM-ddTHH:mm:ss")

# N개의 JSON 라인을 생성 (각각 고유한 eventId, orderId)
$lines = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $Count; $i++) {
    $eventId = [guid]::NewGuid().ToString()
    $orderId = [guid]::NewGuid().ToString()
    $dropId  = [guid]::NewGuid().ToString()
    $userId  = [guid]::NewGuid().ToString()

    # 한 줄 JSON (kafka-console-producer는 한 줄 = 한 메시지)
    $json = "{`"eventId`":`"$eventId`",`"orderId`":`"$orderId`",`"dropId`":`"$dropId`",`"userId`":`"$userId`",`"productId`":`"$ProductId`",`"holdExpiresAt`":`"$holdExpires`"}"
    $lines.Add($json)
}

Write-Host "[생성 완료] $($lines.Count) 건의 이벤트 JSON 생성됨. Kafka로 전송 중..." -ForegroundColor Cyan

# 모든 라인을 kafka-console-producer로 파이프 (한 번에 전송)
$lines -join "`n" | docker exec -i $Container kafka-console-producer --bootstrap-server $Broker --topic $Topic

Write-Host "[완료] $Count 건 주입 끝. Grafana(:13000)에서 아웃박스 발행 패널을 확인하세요." -ForegroundColor Green
Write-Host "       order 로그: docker logs omc-order-service --tail 30" -ForegroundColor DarkGray
