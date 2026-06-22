# OMC 프로젝트 코딩 컨벤션 (Coding Conventions)

본 문서는 OMC(MSA) 프로젝트 팀원 모두가 반드시 지켜야 하는 핵심 코딩 규칙과 아키텍처 기준을 정의합니다.

---

## 1. ☕ Java 버전 및 런타임
- **Java 21**을 사용합니다.
- 대용량 트래픽 처리(선착순 드롭, 래플 등)를 위해 Java 21의 **Virtual Threads(가상 스레드)** 이점을 적극 활용합니다.

---

## 2. 🗄️ Entity (엔티티) 생성 규칙
대기업 시니어 레벨의 견고한 Entity 설계를 강제합니다.

- **`@Builder` 클래스 레벨 선언 금지**: 무분별한 객체 생성을 막기 위해 클래스 상단에 `@Builder`를 달지 않습니다.
- **`private` 생성자 + `@Builder`**: 생성 시점에 반드시 필요한 필드만 포함하는 `private` 생성자를 만들고, 그 위에 `@Builder`를 선언합니다.
- **정적 팩토리 메서드 `create()` 필수**: 외부에서는 오직 `Entity.create(...)` 메서드를 통해서만 객체를 생성해야 합니다. 객체 생성 시 필요한 검증 로직은 이 `create()` 내부에서 수행합니다.
- **`@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수**: JPA(Hibernate) 프레임워크만 프록시 객체를 생성할 수 있도록 기본 생성자를 열어두되, 개발자가 코드에서 `new Entity()`를 호출하는 것은 차단합니다.

```java
// ✅ 올바른 Entity 예시
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @Builder
    private UserEntity(String name) {
        this.name = name;
    }

    public static UserEntity create(String name) {
        return UserEntity.builder()
            .name(name)
            .build();
    }
}
```

---

## 3. 📦 DTO (Data Transfer Object) 생성 규칙
- 모든 DTO는 `class` 대신 **`record`**를 사용합니다.
- 불변성(Immutability)을 보장하여 스레드 안전성(Thread-Safe)을 확보하고, Lombok 보일러플레이트 코드를 제거합니다.

```java
// ✅ 올바른 DTO 예시
public record UserResponse(Long id, String name) {
}
```

---

## 4. 🏛️ 아키텍처 및 계층 (ArchUnit 적용)
스파게티 코드를 방지하기 위해 CI 단계에서 자동 검사되는 규칙입니다.

1. **계층(Layer) 접근 규칙**
   - `Controller` ➡️ `Service` ➡️ `Repository` 방향으로만 접근할 수 있습니다.
   - 역방향 참조나 `Controller`가 `Repository`를 직접 호출하는 것은 엄격히 금지됩니다.
2. **모듈(MSA) 간 직접 침범 금지**
   - `user-service` 내에서 `order-service`의 Java 클래스를 순수하게 `import` 하는 것은 금지됩니다. (반드시 Feign Client, 카프카(Kafka), 또는 `@LoadBalanced`가 적용된 WebClient를 통해 통신해야 합니다.)
   - 단, Spring Cloud Gateway는 WebFlux(Reactive) 기반이므로 블로킹 방식인 Feign Client 대신 **`@LoadBalanced WebClient`** 를 사용합니다.
3. **네이밍 및 어노테이션 규칙**
   - `Controller`: `*Controller` 이름 + `@RestController` (또는 `@Controller`)
   - `Service`: `*Service` 이름 + `@Service`
   - `Repository`: `*Repository` 이름 + `@Repository` (또는 인터페이스 상속)
   - `Entity`: `*Entity` 이름 + `@Entity`
   - `DTO`: 요청은 `*Request`, 응답은 `*Response` 이름 사용

---

## 5. 🐙 Git / 깃허브 사용 규칙
- **커밋 메시지 언어**: 무조건 **한국어**로 작성합니다.
- **커밋 컨벤션**: 
  - `✨ feat:` 새로운 기능 추가
  - `🐛 fix:` 버그 수정
  - `♻️ refactor:` 코드 리팩토링 (기능 변화 없음)
  - `chore:` 빌드, 설정, 라벨 등 잡무
  - `test:` 테스트 코드 작성
  - `docs:` 문서 작성
- **PR 필수**: `develop` 또는 `main` 브랜치에 직접 푸시(Direct Push)하는 것은 관리자 권한을 제외하고 금지됩니다. 반드시 새로운 브랜치를 만들고 Pull Request를 거쳐야 합니다.

---

## 6. 📂 패키지 구조 컨벤션 (DDD 4계층)
우리 프로젝트의 모든 마이크로서비스는 아래의 4계층 구조를 엄격하게 따릅니다. 카프카(SAGA) 및 레디스 인프라 코드를 비즈니스 로직과 완벽히 격리하는 것이 목적입니다. 패키지 루트는 `com.omc.{service-name}` 입니다.

```text
com.omc.{service-name}
├── presentation/
│   ├── controller/
│   └── dto/
│       ├── request/
│       └── response/
├── application/
│   ├── service/              ← 비즈니스 로직 (유스케이스)
│   ├── scheduler/            ← @Scheduled (드롭 오픈/종료, hold 만료, 배송, 추첨 등)
│   └── event/
│       ├── producer/         ← 이벤트 발행 (application이 "무엇을" 발행할지)
│       └── consumer/         ← 이벤트 수신 핸들러 (수신 후 유스케이스 호출)
├── domain/
│   ├── entity/               ← JPA 엔티티
│   ├── enums/                ← 도메인 enum (상태/역할 등, 단일 서비스 전용 중립 구역)
│   ├── repository/           ← JPA Repository 인터페이스 (구현 없음)
│   ├── exception/            ← ErrorCode enum, 도메인 예외
│   └── vo/                   ← 값 객체 (선택)
└── infrastructure/
    ├── persistence/          ← Repository 구현 / QueryDSL (필요 시)
    ├── redis/                ← RedisTemplate, Lua Script (drop/raffle 비중 큼)
    ├── kafka/                ← KafkaTemplate 설정, 직렬화, 실제 send/listen 어댑터
    ├── client/               ← FeignClient, WebClientConfig (서비스 간 통신 어댑터)
    └── config/               ← @Configuration
```

### 🚨 [핵심 주의사항] Enum 생성 위치
`Status`, `Role`, `Type` 등의 Enum 클래스는 **절대 `domain/entity`나 `presentation/dto` 폴더에 생성하지 마십시오.** 
모든 Enum은 반드시 **`domain/enums`** 패키지에 생성해야 하며, DTO와 Entity는 이 `domain/enums` 패키지를 `import`하여 사용합니다. (계층 침범 ArchUnit 에러 원천 차단)
