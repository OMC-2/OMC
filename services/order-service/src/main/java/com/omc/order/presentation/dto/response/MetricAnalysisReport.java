package com.omc.order.presentation.dto.response;

import java.util.List;
import java.util.Map;

//API 최종 응답 리포트
//LLM 분석 결과에 더해, 코드가 수집한 원본 지표(rawMetrics)와 Hard Rule 판정 여부를 포함
public record MetricAnalysisReport(
    String status, //최종 상태(Hard Rule 우선, 없으면 LLM 판단)
    String summary, //한 줄 요약(LLM)
    List<String> keyMetrics, //근거 지표(LLM)
    String answer, //답변 + 권장 조치(LLM)
    List<String> hardRuleAlerts, //코드가 확정한 위험 근거(비어있으면 Hard Rule 미발동)
    Map<String, String> rawMetrics //수집한 원본 지표 8종 (코드, 검증용)
) {}
