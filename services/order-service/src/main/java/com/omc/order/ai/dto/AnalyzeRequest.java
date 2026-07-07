package com.omc.order.ai.dto;

//관리자가 자연어로 입력하는 질문
//예 : {"question": "요즘 주문 응답이 느린데 어디가 문제야?"}
public record AnalyzeRequest(
    String question
) {}
