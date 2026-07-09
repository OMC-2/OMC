package com.omc.order.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

//Prometheus HTTP API(/api/v1/query)로 order-service의 핵심 메트릭을 조회
//LLM 진단에 필요한 "현재 스냅샷"을 instant query로 가져옴
//각 쿼리는 PromQL 이며, 결과에서 스킬라 값만 뽑아 Map으로 정리

@Slf4j
@Component
public class PrometheusClient {

  private final String prometheusUrl;
  private final RestClient restClient;

  public PrometheusClient(@Value("${diagnosis.prometheus-url}") String prometheusUrl) {
    this.prometheusUrl = prometheusUrl;
    this.restClient = RestClient.builder().build();
  }

  //진단용 핵심 메트릭 스냅샷 조회
  //반환: 지표명 -> 값(문자열), LLM 프롬프트에 그대로 넣기 좋게 정리

  public Map<String, String> collectOrderMetrics() {
    Map<String, String> metrics = new LinkedHashMap<>();

    //API 계층
    //1) API 응답시간 p99
    metrics.put("api_p99_response_seconds", query("histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application=\"order-service\"}[5m])) by (le))"));

    //2) API 처리량 (req/s)
    metrics.put("api_throughput_rps", query("sum(rate(http_server_requests_seconds_count{application=\"order-service\"}[1m]))"));

    //이벤트(Outbox 발행) 계층
    //3) 아웃박스 발행 지연 p99(초) - 롤러 적체 지표
    metrics.put("outbox_publish_lag_p99_seconds", query("order_outbox_publish_lag_seconds{application=\"order-service\", quantile=\"0.99\"}"));

    //4) 아웃박스 발행 식패 누적 - 발행 실패 격리 지표
    metrics.put("outbox_publish_failure_total", query("sum(order_outbox_publish_failure_total{application=\"order-service\"})"));

    //5) 아웃박스 발행 DLQ 적재 수
    metrics.put("outbox_publish_dlq_total", query("sum(order_outbox_publish_dlq_total{application=\"order-service\"})"));

    //이벤트(Consumer 수신) 계층
    //6) Consumer 처리 실패 DLQ 적재 수(product 장애 등 실패 신호)
    metrics.put("consumer_dlq_total", query(
        "sum(order_consumer_dlq_total{application=\"order-service\"})"));    //6) Circuit Breaker OPEN 상태 여부 (product 연동)

    //외부 연동(Circuit Breaker)계층
    //7) Circuit Breaker OPEN 상태 여부 (product 연동)
    metrics.put("circuitbreaker_open", query("resilience4j_circuitbreaker_state{application=\"order-service\", state=\"open\"}"));

    //8) Circuit Breaker 실패 호출 수
    metrics.put("circuitbreaker_failed_calls", query("sum(resilience4j_circuitbreaker_calls{application=\"order-service\", kind=\"failed\"})"));

    //리소스(JVM/DB/스레드) 계층
    //9) JVM 힙 사용률(bytes)
    metrics.put("jvm_heap_used_bytes", query("sum(jvm_memory_used_bytes{application=\"order-service\", area=\"heap\"})"));

    //10) DB 커넥션풀 사용률 (active/max, 0.0~1.0) -1.0 근접 시 풀 고갈
    metrics.put("db_pool_usage_ratio", query(
        "hikaricp_connections_active{application=\"order-service\"} / hikaricp_connections_max{application=\"order-service\"}"));

    //11) DB 커넥션 대기 수 (pending) - 0보다 크면 커넥션을 못 얻어 대기 중(풀 부족)
    metrics.put("db_connections_pending", query(
        "hikaricp_connections_pending{application=\"order-service\"}"));

    //12) JVM 라이브 스레드 수 (톰캣 busy 스레드 미노출 환경의 스레드 포화 근사 지표)
    metrics.put("jvm_live_threads", query(
        "jvm_threads_live_threads{application=\"order-service\"}"));

    return metrics;
  }

  //단일 PromQL instant query 실행 -> 스칼라 값 문자열 반환
  //결과가 없거나 오류면 "N/A"로 반환하여 진단 흐름이 끊기지 않게 함
  private String query(String promql) {
    try {
      String encoded = URLEncoder.encode(promql, StandardCharsets.UTF_8);
      URI uri = URI.create(prometheusUrl + "/api/v1/query?query=" + encoded);

      JsonNode root = restClient.get()
          .uri(uri)
          .retrieve()
          .body(JsonNode.class);

      if (root == null) {
        return "N/A";
      }

      JsonNode result = root.path("data").path("result");
      if (!result.isArray() || result.isEmpty()) {
        return "N/A";
      }

      //instant query 결과: result[0].value = [timestamp, "value"]
      JsonNode value = result.get(0).path("value");
      if (value.isArray() && value.size() == 2) {
        return value.get(1).asText();
      }
        return "N/A";

    } catch (Exception e) {
      log.warn("[PrometheusClient] 메트릭 조회 실패, query={}, error={}", promql, e.getMessage());
      return "N/A";
    }
  }
}
