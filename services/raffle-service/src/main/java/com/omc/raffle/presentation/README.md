# Raffle Service - Presentation Layer

## 📌 역할 (Role)
외부(클라이언트)로부터 들어오는 HTTP 요청을 받아 검증하고, 알맞은 Service 로직을 호출한 뒤 응답을 반환하는 계층입니다. (Controller, Request/Response DTO)

## 🏛️ 핵심 설계 의도
1. **헤더 기반 권한 추출**: Gateway에서 검증된 `X-User-Id`를 `@RequestHeader`로 추출하여 안전하게 사용합니다.
2. **DTO 분리**: 클라이언트가 전송하는 Body(`presentation.dto.request`)와 서비스가 필요로 하는 객체(`application.dto.request`)를 분리하여 의존성을 단방향으로 유지합니다.
3. **공통 응답 규격**: 모든 API 응답은 `com.omc.common.response.ApiResponse`로 감싸서 일관된 포맷(`success`, `status`, `data`)으로 내려줍니다.

## 🚨 제약 사항
- `record` 사용 시, 어떠한 경우에도 롬복 어노테이션(`@Builder` 등)을 추가하지 않습니다.
- 프레젠테이션 계층(Controller) 내부에 비즈니스 로직(예: 예외 처리, 상태 검증)을 직접 작성하지 않습니다. 모든 비즈니스 규칙은 Service에서 담당합니다.
