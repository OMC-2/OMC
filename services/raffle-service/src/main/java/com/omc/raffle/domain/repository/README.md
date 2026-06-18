# Raffle Service - Repository Layer (Data Access)

## 📌 역할 (Role)
이 패키지는 `raffle-service`의 영속성 계층(Persistence Layer)을 담당하며, Spring Data JPA를 기반으로 `Raffle`, `RaffleEntry`, `RaffleResult` 엔티티의 데이터 접근 로직을 캡슐화합니다.

## 🏛️ 핵심 설계 의도 (Architecture & Design Intent)
1. **순수 도메인 분리 유지**: JPA 인터페이스를 하위 `infrastructure`가 아닌 `domain/repository`에 위치시켜 의존성 역전 원칙(DIP)에 가까운 구조를 가져갑니다. (비즈니스 로직이 영속성 계층에 종속되지 않도록 보호)
2. **Soft Delete 자동 적용**: `Raffle`, `RaffleEntry` 등 핵심 엔티티에는 `@SQLRestriction("deleted_at IS NULL")`이 선언되어 있습니다. 따라서 기본 제공되는 `findById()`, `findAll()` 등의 메서드 호출 시 삭제된 데이터는 쿼리 레벨에서 자동으로 필터링됩니다.
3. **CQRS 분리 고려**: 이 패키지에는 단일/단순 조회용 JPA 메서드만 선언합니다. 페이징이나 복잡한 다이나믹 쿼리가 필요한 경우 여기(JPA 인터페이스)에 억지로 만들지 않고, 향후 `infrastructure/persistence` 패키지의 QueryDSL 구현체로 분리하여 작성합니다.

## 🚨 다른 개발자가 주의해야 할 점 (Caveats)
- **Native Query 지양**: `@Query(nativeQuery = true)` 사용 시 엔티티에 걸린 Soft Delete 필터(`deleted_at IS NULL`)가 무시됩니다! 가급적 JPQL이나 Query 메서드(`findBy...`)를 우선 사용하시고, 불가피할 경우 쿼리 내에 삭제 여부 조건을 직접 명시해야 합니다.
- **Race Condition 방지 (매우 중요)**: 
  `RaffleEntryRepository.existsByDropIdAndUserId` 메서드를 통해 중복 응모 여부를 검사할 수 있지만, 이는 **RDBMS 레벨이므로 대규모 트래픽에서 동시성(Race Condition) 이슈를 완벽하게 막을 수 없습니다.**
  - **해결책**: 이 메서드를 믿고 로직을 짜지 마세요. 반드시 Redis의 `SADD` 원자적 연산을 선행하여 동시성 검증을 1차로 통과한 요청만 JPA Repository를 호출하도록 서비스 로직을 짜야 한다 (관련 정책: `docs/01.business-policy.md`)
