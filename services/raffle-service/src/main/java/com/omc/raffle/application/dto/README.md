# Raffle Service - Application DTO Layer

## 📌 역할 (Role)
프레젠테이션 계층(Controller)과 애플리케이션 계층(Service) 사이의 데이터 전달을 담당하는 객체(Data Transfer Object)들을 모아둔 패키지입니다.

## 🏛️ 핵심 설계 의도
1. **Record 우선 사용**: DTO는 상태를 가지지 않고 불변이어야 하므로, 자바 14+의 `record`를 사용하여 보일러플레이트를 줄이고 불변성을 강제합니다.
2. **DTO 변환 위치**: 
   - Entity -> Response DTO 변환: DTO 내부의 정적 팩토리 메서드(`from()`, `of()`)를 사용합니다. (Service에서 이 메서드를 호출하여 반환)
   - Request DTO -> 값 변환: Service 계층 파라미터로 그대로 전달하거나 필요한 값만 꺼내서 사용합니다.

## 🚨 다른 개발자가 주의해야 할 점
- 엔티티 객체를 절대로 Controller로 직접 반환하지 마세요. 반드시 이 패키지에 Response DTO를 만들어 변환해야 합니다.
