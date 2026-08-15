# JourneyOps Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 네 개의 Spring Boot API에서 대출 여정을 실행하고, RestClient 추적·ECS 구조화 로그·Elastic/Kibana 퍼널·선택적 Sentry 오류 수집을 로컬 Docker Compose로 검증할 수 있는 최소 데모를 만듭니다.

**Architecture:** `user-api`, `loan-application-api`, `loan-evaluation-api`, `loan-contract-api`를 독립 애플리케이션으로 실행합니다. `loan-application-api`만 신청 상태를 소유하고 다른 서비스는 자동 구성된 `RestClient.Builder`로 사용자와 신청을 검증한 뒤 상태 전이를 요청합니다. `observability-log`는 고정된 도메인 이벤트 이름, ECS 키, HTTP 요청 로그 필터를 제공하며 각 서비스의 JSON 로그는 Filebeat를 통해 Elasticsearch로 전송됩니다.

**Tech Stack:** Kotlin 2.2.21, Java 21, Spring Boot 4.0.3, Gradle 9.4, Spring MVC `RestClient`, Micrometer Tracing OpenTelemetry bridge, Sentry Java 8.37.1, Elasticsearch/Kibana/Filebeat 9.3.1, Docker Compose, JUnit 5, MockWebServer

## Global Constraints

- 프로젝트 이름은 `journeyops`, 기본 패키지는 `dev.journeyops`를 사용합니다.
- 서비스 간 통신은 Spring의 자동 구성된 `RestClient.Builder`로만 생성한 `RestClient`를 사용합니다.
- 영속 저장소, Kafka, Kubernetes, 실제 인증 사업자 연동은 구현하지 않습니다.
- 클라이언트는 access token과 `applicationId`만 보관합니다.
- 로그에 access token, 휴대폰 번호, 주민등록번호, 신분증 원문, 요청 본문을 기록하지 않습니다.
- 도메인 이벤트는 `event.action`, `event.id`, `event.dataset`, `event.outcome`, `user.id`, `loan.application.id` 필드를 사용합니다.
- 오류 응답은 RFC 9457 Problem Details와 `code`, `requestId` 확장 필드를 사용합니다.
- Sentry는 `SENTRY_DSN`이 비어 있으면 외부 전송 없이 동작하고 `send-default-pii=false`를 유지합니다.
- 모든 기능은 실패하는 테스트를 먼저 확인한 뒤 최소 구현으로 통과시킵니다.
- 각 커밋은 저장소의 Lore Commit Protocol을 따릅니다.

---

## File Structure

```text
observability-log/
  src/main/kotlin/dev/journeyops/observability/
    DomainEvent.kt
    DomainEventAction.kt
    DomainEventLogger.kt
    HttpAccessLogFilter.kt
    ObservabilityAutoConfiguration.kt
    RequestId.kt
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/kotlin/dev/journeyops/observability/
    DomainEventLoggerTest.kt
    HttpAccessLogFilterTest.kt

user-api/
  src/main/kotlin/dev/journeyops/user/
    UserApiApplication.kt
    PhoneVerification.kt
    TokenStore.kt
    UserController.kt
    ApplicationClient.kt
    UserProblemHandler.kt
  src/test/kotlin/dev/journeyops/user/
    PhoneVerificationTest.kt
    UserControllerTest.kt
    ApplicationClientTest.kt

loan-application-api/
  src/main/kotlin/dev/journeyops/application/
    LoanApplicationApiApplication.kt
    LoanApplication.kt
    LoanApplicationRepository.kt
    LoanApplicationService.kt
    LoanApplicationController.kt
    InternalLoanApplicationController.kt
    UserClient.kt
    ApplicationProblemHandler.kt
  src/test/kotlin/dev/journeyops/application/
    LoanApplicationTest.kt
    LoanApplicationControllerTest.kt
    UserClientTest.kt

loan-evaluation-api/
  src/main/kotlin/dev/journeyops/evaluation/
    LoanEvaluationApiApplication.kt
    LoanEvaluationController.kt
    UserClient.kt
    ApplicationClient.kt
    EvaluationProblemHandler.kt
  src/test/kotlin/dev/journeyops/evaluation/
    LoanEvaluationControllerTest.kt
    EvaluationClientsTest.kt

loan-contract-api/
  src/main/kotlin/dev/journeyops/contract/
    LoanContractApiApplication.kt
    LoanContractController.kt
    UserClient.kt
    ApplicationClient.kt
    ContractProblemHandler.kt
  src/test/kotlin/dev/journeyops/contract/
    LoanContractControllerTest.kt
    ContractClientsTest.kt

docker/
  Dockerfile
  compose.yml
  filebeat.yml
scripts/
  smoke.sh
docs/
  kibana-funnel.md
```

---

### Task 1: 멀티모듈 실행 골격과 버전 카탈로그

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `buildSrc/src/main/kotlin/global-convention.gradle.kts`
- Create: `observability-log/build.gradle.kts`
- Create: `user-api/build.gradle.kts`
- Create: `loan-application-api/build.gradle.kts`
- Create: `loan-evaluation-api/build.gradle.kts`
- Create: `loan-contract-api/build.gradle.kts`
- Delete: `demo/build.gradle.kts`
- Delete: `demo/src/main/kotlin/Main.kt`

**Interfaces:**
- Produces: Gradle projects `:observability-log`, `:user-api`, `:loan-application-api`, `:loan-evaluation-api`, `:loan-contract-api`
- Produces: aliases `spring-boot-starter-validation`, `micrometer-tracing-bridge-otel`, `sentry-core`, `sentry-spring-boot-4-starter`, `mockwebserver`

- [ ] **Step 1: 변경 전 프로젝트 목록을 기준선으로 확인**

Run: `./gradlew projects`

Expected: `:demo`만 포함되어 있어 새 모듈이 아직 없음을 확인합니다.

- [ ] **Step 2: 프로젝트와 의존성 카탈로그를 변경**

`settings.gradle.kts`의 핵심 내용은 다음과 같이 고정합니다.

```kotlin
rootProject.name = "journeyops"

include(
    "observability-log",
    "user-api",
    "loan-application-api",
    "loan-evaluation-api",
    "loan-contract-api",
)
```

`gradle/libs.versions.toml`에 다음 항목을 추가합니다.

```toml
[versions]
sentry = "8.37.1"
mockwebserver = "5.1.0"

[libraries]
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
micrometer-tracing-bridge-otel = { module = "io.micrometer:micrometer-tracing-bridge-otel" }
sentry-core = { module = "io.sentry:sentry", version.ref = "sentry" }
sentry-spring-boot-4-starter = { module = "io.sentry:sentry-spring-boot-4-starter", version.ref = "sentry" }
mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "mockwebserver" }
```

`observability-log/build.gradle.kts`는 Spring 라이브러리 jar와 Sentry core만 제공합니다.

```kotlin
plugins { id("spring-jar-convention") }

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.sentry.core)
}
```

각 API 모듈은 다음 의존성 집합을 사용하고, 평가·계약·사용자 API처럼 원격 호출 테스트가 있는 모듈에는 `testImplementation(libs.mockwebserver)`를 추가합니다.

```kotlin
plugins { id("spring-boot-convention") }

dependencies {
    implementation(project(":observability-log"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.sentry.spring.boot4.starter)
}
```

- [ ] **Step 3: 모듈 구성이 유효한지 검증**

Run: `./gradlew projects`

Expected: 다섯 개의 새 프로젝트가 출력되고 `:demo`는 출력되지 않습니다.

- [ ] **Step 4: 커밋**

```bash
git add settings.gradle.kts gradle/libs.versions.toml buildSrc observability-log user-api loan-application-api loan-evaluation-api loan-contract-api demo
git commit -m "서비스 경계를 독립 실행 모듈로 고정한다" -m "Constraint: Spring Boot 4.0.3과 Java 21을 유지한다
Confidence: high
Scope-risk: moderate
Tested: ./gradlew projects"
```

---

### Task 2: 공통 ECS 도메인 이벤트와 HTTP 요청 로그

**Files:**
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/DomainEvent.kt`
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/DomainEventAction.kt`
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/DomainEventLogger.kt`
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/RequestId.kt`
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/HttpAccessLogFilter.kt`
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/ObservabilityAutoConfiguration.kt`
- Create: `observability-log/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `observability-log/src/test/kotlin/dev/journeyops/observability/DomainEventLoggerTest.kt`
- Create: `observability-log/src/test/kotlin/dev/journeyops/observability/HttpAccessLogFilterTest.kt`

**Interfaces:**
- Produces: `DomainEvent(eventId: String, action: String, userId: String?, applicationId: String?, outcome: String = "success")`
- Produces: `DomainEventPublisher.publish(event: DomainEvent)` implemented by `DomainEventLogger`
- Produces: `DomainEventAction` string constants for all nine business events
- Produces: servlet filter that accepts or creates `X-Request-Id` and exposes it as response header and MDC field `http.request.id`

- [ ] **Step 1: 도메인 이벤트의 필수 구조를 검증하는 실패 테스트 작성**

```kotlin
@Test
fun `domain event logs stable ECS fields without raw customer data`() {
    val event = DomainEvent(
        eventId = "evt-1",
        action = DomainEventAction.LOAN_APPLICATION_CREATED,
        userId = "sha256:user",
        applicationId = "app-1",
    )

    publisher.publish(event)

    val fields = appender.list.single().keyValuePairs.associate { it.key to it.value }
    assertEquals("evt-1", fields["event.id"])
    assertEquals("loan-application-created", fields["event.action"])
    assertEquals("journeyops.domain-event", fields["event.dataset"])
    assertEquals("app-1", fields["loan.application.id"])
    assertFalse(fields.values.any { it.toString().contains("01012345678") })
}
```

- [ ] **Step 2: 테스트가 대상 타입 부재로 실패하는지 확인**

Run: `./gradlew :observability-log:test --tests '*DomainEventLoggerTest'`

Expected: `DomainEvent`, `DomainEventAction`, `DomainEventLogger`가 없어 컴파일 실패합니다.

- [ ] **Step 3: 최소 이벤트 모델과 로거 구현**

```kotlin
data class DomainEvent(
    val eventId: String,
    val action: String,
    val userId: String? = null,
    val applicationId: String? = null,
    val outcome: String = "success",
)

object DomainEventAction {
    const val PHONE_VERIFICATION_COMPLETED = "phone-verification-completed"
    const val LOAN_APPLICATION_CREATED = "loan-application-created"
    const val LOAN_OFFER_PROVIDED = "loan-offer-provided"
    const val LOAN_APPLICATION_SUBMITTED = "loan-application-submitted"
    const val IDENTITY_CARD_VERIFIED = "identity-card-verified"
    const val LOAN_DOCUMENTS_SUBMITTED = "loan-documents-submitted"
    const val LOAN_EVALUATION_APPROVED = "loan-evaluation-approved"
    const val LOAN_CONTRACT_SIGNED = "loan-contract-signed"
    const val LOAN_PAYMENT_COMPLETED = "loan-payment-completed"
}
```

`DomainEventPublisher` 인터페이스와 실제 구현 `DomainEventLogger`를 만듭니다. 로거는 SLF4J fluent API의 `addKeyValue`를 사용하며 null 필드는 추가하지 않습니다. 메시지는 항상 `domain-event`로 고정합니다.

- [ ] **Step 4: 요청 ID와 HTTP 로그 실패 테스트 작성**

```kotlin
@Test
fun `filter returns request id and never logs authorization header`() {
    val request = MockHttpServletRequest("GET", "/api/v1/loan-applications/app-1")
    request.addHeader("Authorization", "Bearer secret-token")
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    assertNotNull(response.getHeader("X-Request-Id"))
    assertFalse(appender.list.single().formattedMessage.contains("secret-token"))
}
```

- [ ] **Step 5: 요청 필터 최소 구현 후 전체 모듈 테스트**

필터는 `OncePerRequestFilter`를 상속하고 `System.nanoTime()`으로 지연을 계산합니다. 요청 종료 시 `journeyops.http`, HTTP method, URL path, status code, duration, request ID만 key-value로 기록하고 MDC를 반드시 제거합니다.

`ObservabilityAutoConfiguration`은 `DomainEventPublisher`와 `HttpAccessLogFilter`를 조건부 bean으로 등록합니다. imports 파일에는 다음 한 줄을 기록해 각 API의 기본 component scan 범위와 무관하게 적용합니다.

```text
dev.journeyops.observability.ObservabilityAutoConfiguration
```

Run: `./gradlew :observability-log:test`

Expected: 모든 테스트가 통과합니다.

- [ ] **Step 6: 커밋**

```bash
git add observability-log
git commit -m "업무 사건과 요청 로그를 동일한 ECS 규약으로 남긴다" -m "Constraint: 원문 고객 데이터와 인증 헤더는 로그에서 제외한다
Confidence: high
Scope-risk: narrow
Tested: ./gradlew :observability-log:test"
```

---

### Task 3: 신청 상태 머신과 멱등 전이

**Files:**
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/LoanApplication.kt`
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/LoanApplicationRepository.kt`
- Create: `loan-application-api/src/test/kotlin/dev/journeyops/application/LoanApplicationTest.kt`

**Interfaces:**
- Produces: `LoanApplicationState` enum from `CREATED` through `PAID`
- Produces: `LoanApplication.transitionTo(target: LoanApplicationState): TransitionResult`
- Produces: `TransitionResult(applicationId: String, state: LoanApplicationState, eventId: String, changed: Boolean)`
- Produces: `LoanApplicationRepository.save`, `findById`, `getById`

- [ ] **Step 1: 정상 순서, 잘못된 순서, 같은 상태 재요청 테스트 작성**

```kotlin
@Test
fun `application transitions only to the next state`() {
    val application = LoanApplication.create("app-1", "user-1", "evt-created")

    val result = application.transitionTo(LoanApplicationState.OFFER_PROVIDED, "evt-offer")

    assertEquals(LoanApplicationState.OFFER_PROVIDED, result.state)
    assertTrue(result.changed)
}

@Test
fun `application rejects a skipped state`() {
    val application = LoanApplication.create("app-1", "user-1", "evt-created")

    assertThrows<InvalidApplicationStateException> {
        application.transitionTo(LoanApplicationState.DOCUMENTS_SUBMITTED, "evt-documents")
    }
}

@Test
fun `retry returns the original event id without another change`() {
    val application = LoanApplication.create("app-1", "user-1", "evt-created")
    application.transitionTo(LoanApplicationState.OFFER_PROVIDED, "evt-offer")

    val retry = application.transitionTo(LoanApplicationState.OFFER_PROVIDED, "evt-new")

    assertEquals("evt-offer", retry.eventId)
    assertFalse(retry.changed)
}
```

- [ ] **Step 2: 상태 모델 부재로 실패 확인**

Run: `./gradlew :loan-application-api:test --tests '*LoanApplicationTest'`

Expected: 대상 타입이 없어 컴파일 실패합니다.

- [ ] **Step 3: 상태 머신과 인메모리 저장소 구현**

```kotlin
enum class LoanApplicationState {
    CREATED,
    OFFER_PROVIDED,
    APPLICATION_SUBMITTED,
    IDENTITY_VERIFIED,
    DOCUMENTS_SUBMITTED,
    EVALUATION_APPROVED,
    CONTRACT_SIGNED,
    PAID,
}

fun transitionTo(target: LoanApplicationState, proposedEventId: String): TransitionResult {
    eventIds[target]?.let { return TransitionResult(id, state, it, false) }
    val expected = LoanApplicationState.entries[state.ordinal + 1]
    if (target != expected) throw InvalidApplicationStateException(state, target)
    state = target
    eventIds[target] = proposedEventId
    return TransitionResult(id, state, proposedEventId, true)
}
```

마지막 상태에서 다른 전이를 요청할 때 배열 범위를 벗어나지 않고 `InvalidApplicationStateException`을 던지도록 분기합니다. 저장소는 `ConcurrentHashMap<String, LoanApplication>`을 사용합니다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :loan-application-api:test --tests '*LoanApplicationTest'`

Expected: 세 상태 테스트가 통과합니다.

- [ ] **Step 5: 커밋**

```bash
git add loan-application-api
git commit -m "대출 여정의 순서와 재시도 의미를 상태 머신에 둔다" -m "Rejected: 컨트롤러별 상태 검사 | 상태 규칙이 분산된다
Confidence: high
Scope-risk: narrow
Tested: ./gradlew :loan-application-api:test --tests '*LoanApplicationTest'"
```

---

### Task 4: 휴대폰 인증과 데모 토큰 해석

**Files:**
- Create: `user-api/src/main/kotlin/dev/journeyops/user/UserApiApplication.kt`
- Create: `user-api/src/main/kotlin/dev/journeyops/user/PhoneVerification.kt`
- Create: `user-api/src/main/kotlin/dev/journeyops/user/TokenStore.kt`
- Create: `user-api/src/main/kotlin/dev/journeyops/user/UserController.kt`
- Create: `user-api/src/main/kotlin/dev/journeyops/user/UserProblemHandler.kt`
- Create: `user-api/src/main/resources/application.yml`
- Create: `user-api/src/test/kotlin/dev/journeyops/user/PhoneVerificationTest.kt`
- Create: `user-api/src/test/kotlin/dev/journeyops/user/UserControllerTest.kt`

**Interfaces:**
- Produces: `POST /api/v1/phone-verifications` with `PhoneVerificationRequest(phoneNumber)` and `PhoneVerificationResponse(accessToken, userId)`
- Produces: `GET /internal/v1/users/me` resolving the bearer token to `UserResponse(userId)`
- Produces: `PhoneVerifier.verify(phoneNumber): VerifiedUser`

- [ ] **Step 1: 인증 결과와 로그 안전성 테스트 작성**

```kotlin
@Test
fun `verified phone produces stable pseudonymous user and opaque token`() {
    val result = verifier.verify("010-1234-5678")

    assertTrue(result.userId.startsWith("sha256:"))
    assertFalse(result.userId.contains("010"))
    assertFalse(result.accessToken.contains("010"))
}
```

컨트롤러 테스트는 유효하지 않은 번호에 `400`, 없는 토큰에 `401`, 정상 토큰 해석에 `200`을 검증합니다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :user-api:test --tests '*PhoneVerificationTest' --tests '*UserControllerTest'`

Expected: 사용자 인증 타입과 컨트롤러가 없어 컴파일 실패합니다.

- [ ] **Step 3: SHA-256 사용자 ID, 무작위 토큰, API 구현**

```kotlin
fun verify(phoneNumber: String): VerifiedUser {
    val normalized = phoneNumber.filter(Char::isDigit)
    require(normalized.matches(Regex("01[016789][0-9]{7,8}")))
    val userId = "sha256:" + sha256(normalized)
    val accessToken = UUID.randomUUID().toString()
    tokenStore.save(accessToken, userId)
    eventPublisher.publish(DomainEvent(UUID.randomUUID().toString(), PHONE_VERIFICATION_COMPLETED, userId))
    return VerifiedUser(userId, accessToken)
}
```

내부 사용자 API는 Authorization 값을 로깅하지 않고 `Bearer ` 접두사 뒤의 토큰만 `TokenStore`에서 조회합니다.

- [ ] **Step 4: 서비스 설정 추가**

```yaml
server:
  port: 8081
spring:
  application:
    name: user-api
logging:
  structured:
    format:
      console: ecs
      file: ecs
    json:
      rename:
        traceId: trace.id
        spanId: span.id
  file:
    name: ${JOURNEYOPS_LOG_FILE:build/logs/user-api.json}
management:
  tracing:
    sampling:
      probability: 1.0
sentry:
  dsn: ${SENTRY_DSN:}
  send-default-pii: false
```

- [ ] **Step 5: 테스트와 기동 검증**

Run: `./gradlew :user-api:test :user-api:bootJar`

Expected: 테스트와 실행 jar 생성이 성공합니다.

- [ ] **Step 6: 커밋**

```bash
git add user-api
git commit -m "원문 식별정보 없이 대출 여정의 사용자를 연결한다" -m "Constraint: 실제 인증 토큰 보안은 데모 범위에서 제외한다
Confidence: high
Scope-risk: moderate
Tested: ./gradlew :user-api:test :user-api:bootJar"
```

---

### Task 5: 신청 외부 API와 내부 상태 전이 계약

**Files:**
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/LoanApplicationApiApplication.kt`
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/LoanApplicationService.kt`
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/LoanApplicationController.kt`
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/InternalLoanApplicationController.kt`
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/UserClient.kt`
- Create: `loan-application-api/src/main/kotlin/dev/journeyops/application/ApplicationProblemHandler.kt`
- Create: `loan-application-api/src/main/resources/application.yml`
- Create: `loan-application-api/src/test/kotlin/dev/journeyops/application/LoanApplicationControllerTest.kt`
- Create: `loan-application-api/src/test/kotlin/dev/journeyops/application/UserClientTest.kt`

**Interfaces:**
- Produces: `POST /api/v1/loan-applications`
- Produces: `POST /api/v1/loan-applications/{id}/submit`
- Produces: `POST /api/v1/loan-applications/{id}/documents`
- Produces: `GET /internal/v1/loan-applications/{id}` with owner and state
- Produces: `POST /internal/v1/loan-applications/{id}/transitions` with target state and owner

- [ ] **Step 1: 컨트롤러와 사용자 RestClient 실패 테스트 작성**

```kotlin
@Test
fun `create returns application id and CREATED state`() {
    val response = controller.create("Bearer token")

    assertEquals(HttpStatus.CREATED, response.statusCode)
    assertEquals(LoanApplicationState.CREATED, response.body!!.state)
}

@Test
fun `user client maps unavailable upstream to service unavailable`() {
    server.shutdown()

    assertThrows<UpstreamUnavailableException> {
        client.resolveUser("Bearer token")
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :loan-application-api:test --tests '*LoanApplicationControllerTest' --tests '*UserClientTest'`

Expected: 서비스, 컨트롤러, 클라이언트가 없어 컴파일 실패합니다.

- [ ] **Step 3: 자동 구성 RestClient와 신청 서비스 구현**

```kotlin
@Component
class UserClient(builder: RestClient.Builder, properties: UserApiProperties) {
    private val client = builder.baseUrl(properties.baseUrl).build()

    fun resolveUser(authorization: String): String =
        client.get()
            .uri("/internal/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .retrieve()
            .body(UserResponse::class.java)!!
            .userId
}
```

신청 생성은 UUID 기반 `applicationId`와 `eventId`를 생성하고 저장한 후 `loan-application-created`를 기록합니다. 신청서와 서류 제출은 도메인 전이를 수행하고 `changed=true`일 때만 각각의 사건을 기록합니다.

- [ ] **Step 4: 내부 전이의 소유자 검증과 멱등 응답 구현**

```kotlin
data class TransitionRequest(val userId: String, val targetState: LoanApplicationState)

fun transition(id: String, request: TransitionRequest): TransitionResponse {
    val application = repository.getById(id)
    if (application.userId != request.userId) throw ApplicationNotFoundException(id)
    val result = application.transitionTo(request.targetState, UUID.randomUUID().toString())
    return TransitionResponse(result.applicationId, result.state, result.eventId, result.changed)
}
```

- [ ] **Step 5: Problem Details 계약 추가**

`ApplicationProblemHandler`는 `InvalidApplicationStateException`을 409, 없는 신청을 404, 사용자 API 장애를 503으로 변환하고 `ProblemDetail.properties`에 `code`, 현재 request ID를 넣습니다.

- [ ] **Step 6: 테스트와 jar 검증**

Run: `./gradlew :loan-application-api:test :loan-application-api:bootJar`

Expected: 테스트와 jar 생성이 성공합니다.

- [ ] **Step 7: 커밋**

```bash
git add loan-application-api
git commit -m "신청 상태의 단일 소유권을 HTTP 계약으로 보호한다" -m "Rejected: 각 서비스의 신청 상태 복제 | 동기식 데모에서 불일치만 늘어난다
Confidence: high
Scope-risk: moderate
Tested: ./gradlew :loan-application-api:test :loan-application-api:bootJar"
```

---

### Task 6: 한도조회와 대출 심사 서비스

**Files:**
- Create: `loan-evaluation-api/src/main/kotlin/dev/journeyops/evaluation/LoanEvaluationApiApplication.kt`
- Create: `loan-evaluation-api/src/main/kotlin/dev/journeyops/evaluation/LoanEvaluationController.kt`
- Create: `loan-evaluation-api/src/main/kotlin/dev/journeyops/evaluation/UserClient.kt`
- Create: `loan-evaluation-api/src/main/kotlin/dev/journeyops/evaluation/ApplicationClient.kt`
- Create: `loan-evaluation-api/src/main/kotlin/dev/journeyops/evaluation/EvaluationProblemHandler.kt`
- Create: `loan-evaluation-api/src/main/resources/application.yml`
- Create: `loan-evaluation-api/src/test/kotlin/dev/journeyops/evaluation/LoanEvaluationControllerTest.kt`
- Create: `loan-evaluation-api/src/test/kotlin/dev/journeyops/evaluation/EvaluationClientsTest.kt`

**Interfaces:**
- Produces: `POST /api/v1/loan-applications/{id}/limit-inquiries` returning fixed demo offer amount `30_000_000`
- Produces: `POST /api/v1/loan-applications/{id}/evaluations` returning `APPROVED`
- Consumes: user resolution API and application transition API from Tasks 4-5

- [ ] **Step 1: 한도와 심사 사건 테스트 작성**

```kotlin
@Test
fun `limit inquiry transitions application and logs offer event`() {
    val response = controller.inquire("app-1", "Bearer token")

    assertEquals(30_000_000L, response.approvedAmount)
    assertEquals("loan-offer-provided", loggedEvent.action)
    assertEquals("evt-offer", loggedEvent.eventId)
}

@Test
fun `evaluation approval logs only when transition changed`() {
    applicationClient.next = TransitionResponse("app-1", EVALUATION_APPROVED, "evt-eval", false)

    controller.evaluate("app-1", "Bearer token")

    assertTrue(eventSink.events.isEmpty())
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :loan-evaluation-api:test --tests '*LoanEvaluationControllerTest'`

Expected: 평가 컨트롤러가 없어 컴파일 실패합니다.

- [ ] **Step 3: 사용자 확인, 상태 전이, 이벤트 기록 구현**

각 요청은 토큰으로 `userId`를 확인하고 신청 내부 API에 `OFFER_PROVIDED` 또는 `EVALUATION_APPROVED`를 요청합니다. 응답의 `changed=true`일 때만 응답의 `eventId`로 도메인 이벤트를 기록합니다.

원격 401은 401, 404는 404, 409는 409로 의미를 유지하고 연결 실패와 5xx는 503으로 변환합니다.

- [ ] **Step 4: 설정과 전체 모듈 테스트**

`application.yml`은 포트 `8083`, 서비스명 `loan-evaluation-api`, `USER_API_BASE_URL`, `LOAN_APPLICATION_API_BASE_URL`, ECS 로깅, 추적 샘플링 1.0, 비PII Sentry 설정을 사용합니다.

Run: `./gradlew :loan-evaluation-api:test :loan-evaluation-api:bootJar`

Expected: 테스트와 jar 생성이 성공합니다.

- [ ] **Step 5: 커밋**

```bash
git add loan-evaluation-api
git commit -m "평가 결과를 신청 상태와 동일한 사건 ID로 연결한다" -m "Constraint: 한도와 심사 결과는 결정적인 데모 값으로 고정한다
Confidence: high
Scope-risk: moderate
Tested: ./gradlew :loan-evaluation-api:test :loan-evaluation-api:bootJar"
```

---

### Task 7: 신분증 진위확인 연동

**Files:**
- Create: `user-api/src/main/kotlin/dev/journeyops/user/ApplicationClient.kt`
- Modify: `user-api/src/main/kotlin/dev/journeyops/user/UserController.kt`
- Modify: `user-api/src/main/kotlin/dev/journeyops/user/UserProblemHandler.kt`
- Modify: `user-api/src/main/resources/application.yml`
- Create: `user-api/src/test/kotlin/dev/journeyops/user/ApplicationClientTest.kt`
- Modify: `user-api/src/test/kotlin/dev/journeyops/user/UserControllerTest.kt`

**Interfaces:**
- Produces: `POST /api/v1/loan-applications/{id}/identity-verifications`
- Consumes: application transition API with target `IDENTITY_VERIFIED`

- [ ] **Step 1: 성공, 재시도, 원격 오류 테스트 작성**

```kotlin
@Test
fun `identity verification logs event returned by state owner`() {
    applicationClient.next = TransitionResponse("app-1", IDENTITY_VERIFIED, "evt-id", true)

    val response = controller.verifyIdentity("app-1", "Bearer token")

    assertEquals("VERIFIED", response.result)
    assertEquals("evt-id", eventSink.events.single().eventId)
}
```

재시도 `changed=false`에서는 로그를 추가하지 않고, 원격 409와 503이 Problem Details로 보존되는 테스트를 함께 작성합니다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :user-api:test --tests '*ApplicationClientTest' --tests '*UserControllerTest'`

Expected: 신분증 API와 신청 클라이언트가 없어 실패합니다.

- [ ] **Step 3: RestClient 연동과 사건 기록 구현**

`ApplicationClient.transition`은 사용자 API가 이미 해석한 `userId`와 목표 상태를 내부 신청 API로 전송합니다. 신분증 번호나 이미지 필드는 API 요청 자체에 받지 않고 성공 시뮬레이션만 수행합니다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :user-api:test`

Expected: 휴대폰 인증과 신분증 연동 테스트가 모두 통과합니다.

- [ ] **Step 5: 커밋**

```bash
git add user-api
git commit -m "신분증 확인을 신청 여정에 민감정보 없이 결합한다" -m "Constraint: 신분증 원문 입력과 외부 사업자 호출은 만들지 않는다
Confidence: high
Scope-risk: narrow
Tested: ./gradlew :user-api:test"
```

---

### Task 8: 약정과 지급 서비스

**Files:**
- Create: `loan-contract-api/src/main/kotlin/dev/journeyops/contract/LoanContractApiApplication.kt`
- Create: `loan-contract-api/src/main/kotlin/dev/journeyops/contract/LoanContractController.kt`
- Create: `loan-contract-api/src/main/kotlin/dev/journeyops/contract/UserClient.kt`
- Create: `loan-contract-api/src/main/kotlin/dev/journeyops/contract/ApplicationClient.kt`
- Create: `loan-contract-api/src/main/kotlin/dev/journeyops/contract/ContractProblemHandler.kt`
- Create: `loan-contract-api/src/main/resources/application.yml`
- Create: `loan-contract-api/src/test/kotlin/dev/journeyops/contract/LoanContractControllerTest.kt`
- Create: `loan-contract-api/src/test/kotlin/dev/journeyops/contract/ContractClientsTest.kt`

**Interfaces:**
- Produces: `POST /api/v1/loan-applications/{id}/contracts` returning `SIGNED`
- Produces: `POST /api/v1/loan-applications/{id}/payments` returning `PAID`
- Consumes: user resolution and application transition APIs

- [ ] **Step 1: 약정과 지급 순서 테스트 작성**

```kotlin
@Test
fun `contract and payment use event ids from application transition`() {
    applicationClient.responses.add(TransitionResponse("app-1", CONTRACT_SIGNED, "evt-contract", true))
    applicationClient.responses.add(TransitionResponse("app-1", PAID, "evt-payment", true))

    controller.sign("app-1", "Bearer token")
    controller.pay("app-1", "Bearer token")

    assertEquals(listOf("evt-contract", "evt-payment"), eventSink.events.map { it.eventId })
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :loan-contract-api:test --tests '*LoanContractControllerTest'`

Expected: 계약 컨트롤러와 클라이언트가 없어 컴파일 실패합니다.

- [ ] **Step 3: 계약·지급 구현과 오류 매핑**

약정은 `CONTRACT_SIGNED`, 지급은 `PAID` 전이를 요청하고 각각 `loan-contract-signed`, `loan-payment-completed`를 기록합니다. 신청 서비스의 409를 그대로 반환해 평가 승인 전 약정이나 약정 전 지급을 차단합니다.

- [ ] **Step 4: 설정과 전체 모듈 테스트**

`application.yml`은 포트 `8084`, 서비스명 `loan-contract-api`, 두 upstream URL, ECS 로깅, 추적, Sentry 비PII 설정을 사용합니다.

Run: `./gradlew :loan-contract-api:test :loan-contract-api:bootJar`

Expected: 테스트와 jar 생성이 성공합니다.

- [ ] **Step 5: 커밋**

```bash
git add loan-contract-api
git commit -m "약정과 지급을 검증된 신청 순서 뒤에만 허용한다" -m "Confidence: high
Scope-risk: moderate
Tested: ./gradlew :loan-contract-api:test :loan-contract-api:bootJar"
```

---

### Task 9: 공통 오류 보고와 Sentry 안전 설정

**Files:**
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/ProblemSupport.kt`
- Create: `observability-log/src/main/kotlin/dev/journeyops/observability/SentryErrorReporter.kt`
- Create: `observability-log/src/test/kotlin/dev/journeyops/observability/SentryErrorReporterTest.kt`
- Modify: four `*ProblemHandler.kt` files
- Modify: four `application.yml` files

**Interfaces:**
- Produces: `ProblemSupport.create(status, code, detail, requestId): ProblemDetail`
- Produces: `ErrorReporter.report(throwable: Throwable, serviceName: String, applicationId: String?)`
- Produces: demo-only `POST /api/v1/demo/failures` in `user-api` returning sanitized 500 Problem Details

- [ ] **Step 1: 민감정보 제거와 태그 테스트 작성**

```kotlin
@Test
fun `error reporter sends bounded tags and no authorization value`() {
    reporter.report(IllegalStateException("demo failure"), "user-api", "app-1")

    assertEquals("user-api", captured.tags["service.name"])
    assertEquals("app-1", captured.tags["loan.application.id"])
    assertFalse(captured.tags.values.any { it.contains("Bearer") })
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :observability-log:test --tests '*SentryErrorReporterTest'`

Expected: 오류 보고 타입이 없어 컴파일 실패합니다.

- [ ] **Step 3: Sentry 보고와 공통 ProblemDetail 생성 구현**

`SentryErrorReporter`는 `Sentry.withScope` 안에서 허용된 두 태그만 추가하고 `Sentry.captureException`을 호출합니다. `sentry.send-default-pii=false`, `sentry.max-request-body-size=none`, `sentry.traces-sample-rate=0.0`을 네 서비스에 적용해 Elastic 추적과 역할이 중복되지 않도록 합니다.

- [ ] **Step 4: 네 오류 핸들러가 예상하지 못한 예외만 보고하도록 변경**

400, 401, 404, 409, 503으로 매핑되는 예상 오류는 Sentry issue로 보고하지 않습니다. 처리되지 않은 예외만 `ErrorReporter`에 전달하고 응답 detail은 `Unexpected server error`로 고정합니다.

- [ ] **Step 5: 전체 관련 테스트**

Run: `./gradlew :observability-log:test :user-api:test :loan-application-api:test :loan-evaluation-api:test :loan-contract-api:test`

Expected: 모든 단위·API 계약 테스트가 통과합니다.

- [ ] **Step 6: 커밋**

```bash
git add observability-log user-api loan-application-api loan-evaluation-api loan-contract-api
git commit -m "예상 밖의 서버 오류만 개인정보 없이 Sentry로 보낸다" -m "Rejected: 모든 4xx 보고 | 운영 이슈 신호를 오염시킨다
Confidence: high
Scope-risk: moderate
Tested: ./gradlew :observability-log:test :user-api:test :loan-application-api:test :loan-evaluation-api:test :loan-contract-api:test"
```

---

### Task 10: Docker Compose와 Elastic 수집 경로

**Files:**
- Create: `docker/Dockerfile`
- Create: `docker/compose.yml`
- Create: `docker/filebeat.yml`
- Create: `.dockerignore`
- Modify: `.gitignore`

**Interfaces:**
- Produces: ports 8081-8084, 9200, 5601
- Produces: Elasticsearch indices `journeyops-*`
- Consumes: each service bootJar and JSON log file

- [ ] **Step 1: jar와 Docker 구성 전 검증**

Run: `./gradlew bootJar`

Expected: 네 API jar가 생성됩니다.

- [ ] **Step 2: 공통 이미지 빌드 정의**

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN ./gradlew :${MODULE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3: Compose 서비스와 healthcheck 정의**

`docker/compose.yml`은 API별 `MODULE`, 포트, upstream URL, `/var/log/journeyops` 공유 볼륨을 설정합니다. Elasticsearch, Kibana, Filebeat 이미지는 모두 `9.3.1`로 고정합니다. Elasticsearch는 로컬 전용으로 `discovery.type=single-node`, `xpack.security.enabled=false`, `ES_JAVA_OPTS=-Xms512m -Xmx512m`를 사용합니다.

- [ ] **Step 4: Filebeat NDJSON 입력 정의**

```yaml
filebeat.inputs:
  - type: filestream
    id: journeyops-json
    paths: ["/var/log/journeyops/*.json"]
    parsers:
      - ndjson:
          target: ""
          overwrite_keys: true
          add_error_key: true

output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  index: "journeyops-%{+yyyy.MM.dd}"

setup.template:
  name: "journeyops"
  pattern: "journeyops-*"
```

- [ ] **Step 5: Compose 문법과 이미지 빌드 검증**

Run: `docker compose -f docker/compose.yml config`

Expected: exit code 0이며 모든 서비스와 볼륨이 출력됩니다.

Run: `docker compose -f docker/compose.yml build`

Expected: 네 API 이미지가 성공적으로 생성됩니다.

- [ ] **Step 6: 커밋**

```bash
git add docker .dockerignore .gitignore
git commit -m "한 명령으로 서비스와 로그 검색 환경을 재현한다" -m "Constraint: Elastic 보안 비활성화는 로컬 데모에만 허용한다
Confidence: medium
Scope-risk: moderate
Tested: docker compose -f docker/compose.yml config; docker compose -f docker/compose.yml build"
```

---

### Task 11: 전체 여정 smoke 테스트와 Kibana 사용 문서

**Files:**
- Create: `scripts/smoke.sh`
- Create: `docs/kibana-funnel.md`
- Modify: `README.md`

**Interfaces:**
- Produces: one-command journey verification using `curl` and `jq`
- Produces: Kibana data view, KQL, funnel construction instructions

- [ ] **Step 1: 실패하는 smoke 스크립트 작성**

스크립트는 `set -euo pipefail`을 사용하고 다음 순서로 호출합니다.

```bash
phone_response=$(curl -fsS -X POST http://localhost:8081/api/v1/phone-verifications \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"010-1234-5678"}')
token=$(jq -er '.accessToken' <<<"$phone_response")

application_response=$(curl -fsS -X POST http://localhost:8082/api/v1/loan-applications \
  -H "Authorization: Bearer $token")
application_id=$(jq -er '.applicationId' <<<"$application_response")
```

이후 8083 한도조회, 8082 신청서 제출, 8081 신분증 확인, 8082 서류 제출, 8083 심사, 8084 약정과 지급을 순서대로 호출합니다. 마지막으로 Elasticsearch refresh 후 `loan.application.id.keyword`의 term query로 신청 관련 사건 8개를 검증합니다. 검색 결과에서 `trace.id`가 비어 있지 않은 서비스 간 호출 사건도 하나 이상 검증합니다.

- [ ] **Step 2: 서비스 미기동 상태에서 실패 확인**

Run: `bash scripts/smoke.sh`

Expected: 첫 API 연결 실패로 non-zero 종료합니다.

- [ ] **Step 3: Compose 기동과 전체 여정 검증**

Run: `docker compose -f docker/compose.yml up -d --build`

Run: `bash scripts/smoke.sh`

Expected: `Journey completed: <applicationId>, 8 application events indexed`가 출력됩니다.

- [ ] **Step 4: Kibana 퍼널 문서 작성**

`docs/kibana-funnel.md`에 다음을 정확히 포함합니다.

```text
Data view: journeyops-*
Domain events: event.dataset : "journeyops.domain-event"
Single application: loan.application.id : "<applicationId>"
Trace correlation: trace.id : "<traceId>"

Funnel order:
loan-application-created
loan-offer-provided
loan-application-submitted
identity-card-verified
loan-documents-submitted
loan-evaluation-approved
loan-contract-signed
loan-payment-completed
```

휴대폰 인증은 신청 생성 이전 사건이므로 `user.id`로 연결해 별도 선행 단계로 분석한다고 설명합니다.

- [ ] **Step 5: README에 실행·Sentry·종료 명령 추가**

README에는 `docker compose -f docker/compose.yml up -d --build`, `bash scripts/smoke.sh`, Kibana URL, 선택적 `SENTRY_DSN`, `docker compose -f docker/compose.yml down -v`를 포함합니다.

- [ ] **Step 6: 전체 정적 검증**

Run: `./gradlew check`

Expected: 테스트와 ktlint가 모두 통과합니다.

Run: `git diff --check`

Expected: 출력 없이 exit code 0입니다.

- [ ] **Step 7: 최종 커밋**

```bash
git add scripts docs README.md
git commit -m "대출 여정과 퍼널 분석을 재현 가능한 검증으로 남긴다" -m "Confidence: high
Scope-risk: narrow
Tested: ./gradlew check; bash scripts/smoke.sh; git diff --check"
```

---

## Final Verification

- [ ] `./gradlew clean check`가 성공합니다.
- [ ] `docker compose -f docker/compose.yml config`가 성공합니다.
- [ ] `docker compose -f docker/compose.yml up -d --build` 후 모든 healthcheck가 healthy입니다.
- [ ] `bash scripts/smoke.sh`가 동일 신청 ID의 여덟 사건을 확인합니다.
- [ ] `curl -fsS http://localhost:9200/_cat/indices/journeyops-*?v`에서 인덱스를 확인합니다.
- [ ] `POST /api/v1/demo/failures`가 민감정보 없는 500 Problem Details와 구조화 로그를 남깁니다.
- [ ] `SENTRY_DSN`을 설정하지 않은 상태에서도 모든 API가 정상 기동합니다.
- [ ] `git status --short`에 계획된 변경 외의 파일이 없습니다.

## Official References

- Spring Boot structured logging: <https://docs.spring.io/spring-boot/reference/features/logging.html>
- Spring Boot RestClient: <https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html>
- Spring Boot tracing: <https://docs.spring.io/spring-boot/4.0-SNAPSHOT/reference/actuator/tracing.html>
- Sentry Java SDK: <https://github.com/getsentry/sentry-java/>
- Filebeat Docker: <https://www.elastic.co/guide/en/beats/filebeat/current/running-on-docker.html>
