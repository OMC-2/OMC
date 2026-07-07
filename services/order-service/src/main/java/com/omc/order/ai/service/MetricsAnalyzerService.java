package com.omc.order.ai.service;

import com.omc.order.ai.client.PrometheusClient;
import com.omc.order.ai.dto.LlmAnalysis;
import com.omc.order.ai.dto.MetricAnalysisReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsAnalyzerService {

  private final PrometheusClient prometheusClient;
  private final ChatClient chatClient;

  private static final String DEFAULT_QUESTION = "현재 order-service 상태를 진단해줘.";

  private static final String SYSTEM_PROMPT = """
You are a Site Reliability Engineer (SRE) managing a Microservices Architecture (MSA) system.
Below is the current metric snapshot of the order-service and an administrator's question.
Based on these metrics, answer the administrator's question while adhering to the following principles.

[Metric Interpretation Guide]
- api_p99_response_seconds: Higher value means latency. > 1.0s indicates signs of latency, > 5.0s indicates service paralysis.
- api_throughput_rps: Throughput (req/s). If 0, it may mean no traffic or inbound requests are blocked.
- outbox_publish_lag_p99_seconds: Outbox publish lag. If high, indicates an event poller backlog.
- outbox_publish_failure_total: Cumulative publish failures. > 0 indicates publish failure isolation.
- outbox_publish_dlq_total: DLQ count. > 0 indicates a risk of event loss (CRITICAL).
- consumer_dlq_total: Kafka consumer processing failures moved to DLQ. > 0 means inbound events (e.g. purchase.confirmed) failed to process, often due to a downstream dependency failure such as product-service being down (CRITICAL).
- circuitbreaker_open: If 1, the connection to the product-service is blocked (CRITICAL).
- circuitbreaker_failed_calls: If large, indicates external integration instability.
- jvm_heap_used_bytes: Heap memory usage (bytes).
- db_pool_usage_ratio: DB connection pool usage (active/max, 0.0~1.0). Near 1.0 means the pool is nearly exhausted.
- db_connections_pending: Threads waiting to acquire a DB connection. > 0 means the pool is insufficient and requests are blocked (CRITICAL).
- jvm_live_threads: Total live JVM threads. A sudden spike may indicate thread pool saturation or blocked threads.


[Complex Analysis - IMPORTANT]
Do not evaluate metrics in isolation. Interpret the causality of patterns where multiple metrics worsen together.
Examples:
- If 'circuitbreaker_open' == 1 and 'outbox_publish_dlq_total' increases simultaneously, infer a cascading failure such as "product-service integration failure blocks event processing, so purchase.confirmed events pile up in the DLQ."
- If db_connections_pending > 0 and api_p99 rises together, infer DB pool exhaustion causing request latency.

[Unknown Territory - IMPORTANT]
The provided metrics are core to the order-service's stability, but they are not exhaustive.
- If an issue is suspected despite metrics being in normal ranges, or if the metrics cannot explain the root cause, explicitly state: "수집된 지표로는 명확한 원인을 특정하기 어렵습니다" (It is difficult to pinpoint the exact cause with the collected metrics) Based on the current metric patterns, use your SRE expertise to determine
       and suggest which areas should be investigated further. Do not rely on a fixed list; reason about what the specific metric combination implies
- NEVER hallucinate values not present in the metrics. Treat "N/A" strictly as "No Data" and do not make assumptions.

[Normal State Handling]
- If all metrics are within normal ranges, clearly declare the system healthy and cite the confirming metrics. Do NOT say "cannot pinpoint the cause" for a simply healthy system.
- Reserve "수집된 지표로는 명확한 원인을 특정하기 어렵습니다" ONLY for cases where problem signs exist but metrics cannot fully explain them.

[Output Format]
IMPORTANT: All text must be written concisely in Korean.
- status: Choose one of (정상 / 주의 / 위험)
- summary: A one-line summary
- keyMetrics: 2-3 key metrics used as evidence (Format: "Metric Name: Value")
- answer: Answer to the administrator's question and recommended actions (Must include complex analysis or unknown territory disclaimers if applicable)
""";


  //메트릭 수집 -> LLM 진단 -> LLM 구조화 해석 -> 최종 리포트 생성
  //question이 비어 있으면 기본 진단 질문으로 대체한다(고정 분석 진단 호환)
  public MetricAnalysisReport analyzeMetrics(String question) {
    String userQuestion = StringUtils.hasText(question) ? question : DEFAULT_QUESTION;

    Map<String, String> metrics = prometheusClient.collectOrderMetrics();
    String metricsText = formatMetrics(metrics);
    log.info("[DiagnosisService] 메트릭 수집 완료, LLM 진단 요청. question={}, metrics={}", userQuestion, metrics);


    //1)코드 Hard Rule: 명확한 위험 확정
    List<String> hardRuleAlerts = evaluateHardRules(metrics);

    //2)LLM 구조화 해석
    String userMessage = """
        [현재 order-service 매트릭 스냅샷]
        %s
        
        [관리자 질문]
        %s
        """.formatted(metricsText, userQuestion);

    LlmAnalysis llm = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(userMessage)
        .call()
        .entity(LlmAnalysis.class);

    if (llm == null) {
      llm = new LlmAnalysis("주의", "LLM 분석 결과를 가져오지 못했습니다.", List.of(), "LLM 응답이 비어 있어 지표 원본만 참고하세요.");
    }

    log.info("[MetricsAnalyzerService] LLM 분석 완료, llmStatus={}", llm.status());

    //3)최종 status 결정: Hard Rule 발동 시 무조건 위험(코드 우선), 그 외는 LLM 판단
    String finalStatus = hardRuleAlerts.isEmpty() ? llm.status() : "위험";

    return new MetricAnalysisReport(
        finalStatus,
        llm.summary(),
        llm.keyMetrics(),
        llm.answer(),
        hardRuleAlerts,
        metrics
    );
  }

  //Hard Rule: 논란의 여지 없이 명확하게 위험한 상황만 코드로 확정 (힙/DB/톰캣은 추후 지표 확장 시 추가)
  private List<String> evaluateHardRules(Map<String, String> metrics) {
    List<String> alerts = new ArrayList<>();

    //1)외부 통신 단절(product 서비스 다운)
    if (parseDouble(metrics.get("circuitbreaker_open")) >= 1.0) {
      alerts.add("circuitbreaker_open=1 : product 연동 차단 상태");
    }

    //2)비동기 이벤트 영구 유실 위험 (결제/재고 누락 가능)
    if (parseDouble(metrics.get("outbox_publish_dlq_total")) >0) {
      alerts.add("outbox_publish_dlq_total > 0: 이벤트 DLQ 적재(유실 위험)");
    }

    //3) Consumer 수신 처리 실패 (product 장애 등 실패로 이벤트 처리 불가)
    if (parseDouble(metrics.get("consumer_dlq_total")) >0) {
      alerts.add("consumer_dlq_total>0: 수신 이벤트 DLQ 적재(하류 장애 의심)");
    }

    //4) API 서비스 마비 수준의 극단적 지연
    if (parseDouble(metrics.get("api_p99_response_seconds")) > 5.0) {
      alerts.add("api_p99_response_seconds>5s: 응답 지연 심각(서비스 마비 수준)");
    }

    //5) DB 커넥션 풀 고갈 (커넥션을 못 얻어 대기 중인 요청 존재)
    if (parseDouble(metrics.get("db_connections_pending")) > 0) {
      alerts.add("db_connections_pending>0: DB 커넥션 풀 부족(요청 대기 발생)");
    }

    return alerts;
  }

  //Map -> "지표명: 값" 줄바꿈 텍스트 (프롬프트 삽입용)
  private String formatMetrics(Map<String, String> metrics) {
    return metrics.entrySet().stream()
        .map(e -> "- "+ e.getKey() + ": " + e.getValue())
        .collect(Collectors.joining("\n"));
  }

  //"N/A"/null/빈 값을 0.0으로 안전 처리하여 숫자 비교
  private double parseDouble(String value) {
    if (value == null) {
      return 0.0;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0.0; // "N/A" 등은 0으로 간주 (Hard Rule 오탐 방지)
    }
  }
}
