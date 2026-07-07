package com.omc.order.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.order.ai.dto.AnalyzeRequest;
import com.omc.order.ai.service.MetricsAnalyzerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Metric Analyzer", description = "LLM 기반 메트릭 분석 API")
@RestController
@RequestMapping("/api/v1/admin/analyze-metrics")
@RequiredArgsConstructor
public class MetricsAnalyzerController {

  private final MetricsAnalyzerService metricsAnalyzerService;

  //관리자가 자연어 질문을 입력하면, order-service의 Prometheus 매트릭을 근거로
  //Prometheus 메트릭 스냅샷을 LLM에 보내 SRE 관점의 자연어 분석 리포트를 생성함
  //운영자 직접 호출용(admin). question이 비어있으면 기본 진단으로 동작
  @Operation(summary = "시스템 메트릭 LLM 분석(대화형)", description = "관리자의 질문과 order-service의 Prometheus 메트릭을 수집해 LLM으로 자연어 분석 리포트를 생성합니다.")
  @PostMapping
  public ResponseEntity<ApiResponse<String>> analyzeMetrics(
      @RequestBody(required = false) AnalyzeRequest request) {
    String question = (request == null) ? null : request.question();
    String report = metricsAnalyzerService.analyzeMetrics(question);
    return ResponseEntity.ok(ApiResponse.success(report));
  }
}
