# Raffle Service - Application Service Layer

## 📌 역할 (Role)
프레젠테이션 계층에서 요청을 받아 도메인 모델을 조합하여 비즈니스 유스케이스를 실행하는 서비스 계층입니다. 트랜잭션을 관리하고 외부 서비스(Feign) 연동 및 메시징(Kafka Outbox) 처리를 오케스트레이션합니다.

## 🏛️ 핵심 설계 의도 (Architecture & Design Intent)
1. **유스케이스 분리**: 단일 거대 Service 클래스를 피하고, 목적에 맞게 `RaffleAppService`(사용자 응모), `RaffleDrawService`(관리자/스케줄러 추첨)로 클래스를 분리하여 단일 책임 원칙(SRP)을 준수합니다.
2. **트랜잭션 기본 설정**: 모든 클래스에 기본적으로 `@Transactional(readOnly = true)`를 걸어 조회 성능을 최적화하고 예기치 않은 데이터 수정을 방지합니다. CUD 작업이 필요한 메서드에만 `@Transactional`을 오버라이드하여 사용합니다.
3. **DTO 반환**: 프레젠테이션 계층(Controller)으로 Entity를 절대 노출하지 않고 `RaffleApplyResponse`와 같은 순수 DTO로 변환하여 반환합니다.

## 🚨 다른 개발자가 주의해야 할 점 (Caveats)
- **추첨 로직 셔플 주의**: `Collections.shuffle`을 사용한 인메모리 셔플 방식입니다. 메모리에 올릴 수 없을 만큼 데이터가 방대해질 경우 DB 커서 기반의 무작위 추출이나 다른 알고리즘으로 변경을 고려해야 합니다.
- **의존성 순서**: Service는 절대 Controller나 DTO의 `request` 관련 모듈 외부의 의존성(예: `HttpServletRequest`)을 가져서는 안 됩니다.
