# JourneyOps 로그 분석 가이드

## 로그 계층

| 계층 | `event.dataset` | 용도 | 기본 보존 |
|---|---|---|---|
| HTTP 접근 로그 | `journeyops.http` | 상태 코드, 지연, 경로, 서비스 상태 | 14일 |
| 도메인 사건 | `journeyops.domain-event` | 대출 여정, 전환율, 신청 타임라인 | 90일 |
| 애플리케이션 로그 | 그 외 | 디버깅, 예외, 임시 진단 문맥 | 14일 |

## 필드 사전

| 필드 | 의미 | 사용 예시 |
|---|---|---|
| `service.name` | 로그를 만든 API | 서비스별 오류율 |
| `event.id` | 한 번의 비즈니스 상태 전이 ID | 재시도 중복 판별 |
| `event.action` | 안정적인 비즈니스 사건명 | 퍼널 단계 |
| `event.outcome` | `success` 또는 실패 결과 | 성공률 |
| `event.duration` | 나노초 단위 HTTP 처리 시간 | p95 지연 |
| `http.response.status_code` | HTTP 응답 상태 | 5xx 탐지 |
| `loan.application.id` | 대출 신청 상관관계 ID | 단일 신청 타임라인 |
| `user.id` | 원문 개인정보가 아닌 가명 ID | 인증과 신청 연결 |
| `trace.id`, `span.id` | 서비스 간 호출 상관관계 | 분산 장애 조사 |
| `error.code` | 기계 판독 가능한 오류 코드 | 업스트림 장애 탐지 |
| `custom.*` | 개발 중 자유롭게 추가한 MDC 문맥 | 파트너, 실험군, 배치 ID |

예약 필드는 공통 로깅 모듈이 관리합니다. 개발자가 `LogContext`로 중간에 추가하는 MDC 값은 모두 `custom` 아래에 들어가므로 Elasticsearch 매핑 폭증을 줄입니다. MDC를 직접 사용할 때도 `custom.<field>` 형식을 사용합니다.

```kotlin
logContext.withFields(
    mapOf("partner" to "demo-bank", "experiment" to "limit-v2"),
) {
    logger.info("limit inquiry completed")
}
```

중첩 scope가 끝나면 이전 MDC 값이 복원됩니다. `event.action`, `trace.id` 같은 예약 필드를 임의 문맥으로 덮어쓰지 않습니다.

## Saved Discover

- `JourneyOps - 5xx Errors`: HTTP 5xx 요청을 최신순으로 확인합니다.
- `JourneyOps - Slow Requests`: 1초 이상 요청을 지연 시간순으로 확인합니다.
- `JourneyOps - Upstream Failures`: `UPSTREAM_UNAVAILABLE` 오류를 추적합니다.
- `JourneyOps - Application Timeline`: 신청 한 건을 시간순으로 재구성합니다.
- `JourneyOps - Domain Funnel Events`: 대출 퍼널 사건만 확인합니다.

## 대시보드

- `JourneyOps - Service Health`: 5xx, 느린 요청, 업스트림 실패를 운영 관점에서 봅니다.
- `JourneyOps - Loan Journey`: 도메인 퍼널과 단일 신청 타임라인을 함께 봅니다.
- `JourneyOps - Error Investigation`: 오류에서 `trace.id`와 신청 ID로 원인을 좁힙니다.

대시보드 상단 필터에서 `service.name`, `event.action`, `event.outcome`, `loan.application.id`를 KQL로 자유롭게 조합할 수 있습니다.

## 알림 규칙

| 규칙 | 조건 | 그룹 |
|---|---|---|
| `JourneyOps 5xx Burst` | 5분 동안 HTTP 5xx가 5건 이상 | `service.name` |
| `JourneyOps Upstream Failure Burst` | 5분 동안 업스트림 실패가 3건 이상 | `service.name` |

외부 connector는 데모에서 설정하지 않습니다. Kibana 내부 alert 상태만 생성하며 운영에서는 Slack, PagerDuty 같은 connector와 소유 팀 라우팅을 별도로 구성합니다.

## ES|QL 예시

서비스별 요청 수와 p95 지연:

```esql
FROM journeyops-http-*
| WHERE event.dataset == "journeyops.http"
| STATS requests = COUNT(*), p95_latency = PERCENTILE(event.duration, 95) BY service.name
| SORT p95_latency DESC
```

퍼널 단계별 도달 신청 수:

```esql
FROM journeyops-domain-*
| WHERE event.dataset == "journeyops.domain-event"
| STATS applications = COUNT_DISTINCT(loan.application.id) BY event.action
```

단일 신청 타임라인:

```esql
FROM journeyops-*
| WHERE loan.application.id == "app-456"
| SORT @timestamp ASC
| KEEP @timestamp, service.name, event.action, message, trace.id, error.code
```

## 개인정보와 Sentry

개인정보는 애플리케이션 로거와 Elasticsearch ingest pipeline에서 두 번 마스킹합니다. 휴대폰 번호, 주민등록번호, 이메일, 카드번호, Bearer token, JWT가 대상입니다. Sentry 전송 전에도 같은 마스커를 적용하고 요청 body와 인증 헤더를 제거합니다. 원문 개인정보를 검색 키로 사용하지 말고 `user.id`, `loan.application.id`, `trace.id`를 사용합니다.
