package com.omc.order.ai.service;

import com.omc.order.ai.client.PrometheusClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsAnalyzerService {

  private final PrometheusClient prometheusClient;
  private final ChatClient chatClient;

  private static final String SYSTEM_PROMPT = """
You are a Site Reliability Engineer (SRE) managing a Microservices Architecture (MSA) system.
Below is the current metric snapshot of the order-service and an administrator's question.
Based on these metrics, answer the administrator's question.

[Diagnosis Rules]
- If 'api_p99_response_seconds' is high (e.g., > 1.0s), point out the response latency.
- If 'outbox_publish_lag_p99_seconds' is high or 'outbox_publish_dlq_total' > 0, suspect event publishing backlog/failure.
- If 'circuitbreaker_open' == 1, it means the connection to product-service is blocked.
- If 'circuitbreaker_failed_calls' is large, point out external integration instability.
- If a metric value is "N/A", treat it as "No Data" and do not make assumptions.

[Output Format]
IMPORTANT: You must write the final report in Korean, concisely.
If the administrator's question is irrelevant to the provided metrics, answer only within the scope that can be determined by the metrics and state this limitation clearly.

1) 한 줄 요약 (Start with one of: 정상 / 주의 / 위험)
2) 근거가 된 핵심 지표 2~3개 (Provide 2-3 key metrics as evidence in the format "Metric Name: Value")
3) 답변 및 권장 조치 (Answer to the administrator's question and recommended actions)
""";

  private static final String DEFAULT_QUESTION = "현재 order-service 상태를 진단해줘.";

  //메트릭 수집 -> LLM 진단 -> 자연어 결과 반환
  //question이 비어 있으면 기본 진단 질문으로 대체한다(고정 분석 진단 호환)
  public String analyzeMetrics(String question) {
    String userQuestion = StringUtils.hasText(question) ? question : DEFAULT_QUESTION;

    Map<String, String> metrics = prometheusClient.collectOrderMetrics();
    String metricsText = formatMetrics(metrics);
    log.info("[DiagnosisService] 메트릭 수집 완료, LLM 진단 요청. question={}, metrics={}", userQuestion, metrics);

    String userMessage = """
        [현재 order-service 매트릭 스냅샷[
        %s
        
        [관리자 질문]
        %s
        """.formatted(metricsText, userQuestion);

    String result = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(userMessage)
        .call()
        .content();

    log.info("[DiagnosisService] LLM 분석 완료");
    return result;
  }

  //Map -> "지표명: 값" 줄바꿈 텍스트 (프롬프트 삽입용)
  private String formatMetrics(Map<String, String> metrics) {
    return metrics.entrySet().stream()
        .map(e -> "- "+ e.getKey() + ": " + e.getValue())
        .collect(Collectors.joining("\n"));
  }
}
