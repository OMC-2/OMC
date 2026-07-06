package com.omc.order.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.order.ai.service.MetricsAnalyzerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Metric Analyzer", description = "LLM 기반 메트릭 분석 API")
@RestController
@RequestMapping("/api/v1/admin/analyze-metrics")
@RequiredArgsConstructor
public class MetricsAnalyzerController {

  private final MetricsAnalyzerService metricsAnalyzerService;

  //Prometheus 메트릭 스냅샷을 LLM에 보내 SRE 관점의 자연어 분석을 반환함
  //운영자 직접 호출용(admin). gateway 없이 order-service(8083) 직접 호출도 가능
  @Operation(summary = "시스템 메트릭 LLM 분석", description = "order-service의 Prometheus 메트릭을 수집해 LLM으로 자연어 분석 리포트를 생성합니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<String>> analyzeMetrics() {
    String report = metricsAnalyzerService.analyzeMetrics();
    return ResponseEntity.ok(ApiResponse.success(report));
  }
}
