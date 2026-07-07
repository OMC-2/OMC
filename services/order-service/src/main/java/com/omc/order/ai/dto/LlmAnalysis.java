package com.omc.order.ai.dto;

import java.util.List;

//LLM이 구조화 출력(.entity)으로 채우는 분석 결과
//status는 코드 Hard Rule으로 덮어쓸 수 잇듬(명확한 위험은 코드로 확정함)
public record LlmAnalysis(
    String status, //정상/주의/위험
    String summary, //한 줄 요약
    List<String> keyMetrics, //판단 근거가 된 핵심 지표( 예: "circuitbreaker_open: 1")
    String answer //관리자 질문에 대한 답변 + 권장 조치 (복합 해석 포함)
) {}
