# JourneyOps 최소 데모 설계

## 1. 목표

JourneyOps는 대출 여정을 여러 Spring Boot API로 나누고, 서비스 간 호출과 업무 상태 변경을 관측 가능한 형태로 만드는 데모 프로젝트입니다.

이 데모의 성공 기준은 다음과 같습니다.

- Docker Compose 한 번으로 네 개의 API와 Elasticsearch, Kibana를 실행할 수 있습니다.
- 휴대폰 본인인증부터 대출금 지급까지의 대표 흐름을 HTTP API로 실행할 수 있습니다.
- 서비스 간 통신은 Spring `RestClient`를 사용하며 추적 컨텍스트가 이어집니다.
- 모든 애플리케이션 로그는 JSON 구조화 로그로 남습니다.
- Kibana에서 `loan.application.id`로 한 신청의 전체 여정을 조회할 수 있습니다.
- Kibana에서 `event.action`별 도달 사용자 수를 집계해 퍼널을 구성할 수 있습니다.
- 처리되지 않은 서버 예외는 Sentry로 전달할 수 있으며, DSN이 없어도 로컬 데모는 정상 동작합니다.
- 로그와 Sentry 이벤트에 휴대폰 번호, 주민등록번호, 신분증 이미지 등 민감정보를 저장하지 않습니다.

## 2. 범위

### 포함

- 네 개의 독립 실행 Spring Boot 애플리케이션
- 인메모리 저장소를 이용한 최소 도메인 흐름
- 동기식 `RestClient` 서비스 통신
- ECS에 맞춘 JSON 로그 필드
- Micrometer/OpenTelemetry 기반 trace·span 상관관계
- Elasticsearch와 Kibana 로컬 환경
- 선택적 Sentry 연동
- API 및 도메인 단위 자동화 테스트
- 전체 여정을 실행하는 smoke 스크립트

### 제외

- Kubernetes 매니페스트와 Helm 차트
- Kafka 등 비동기 이벤트 브로커
- 영구 데이터베이스와 분산 트랜잭션
- 실제 휴대폰·신분증 인증 사업자 연동
- 실제 신용평가, 전자서명, 지급결제 연동
- 운영 수준의 인증·인가와 토큰 서명
- 프론트엔드 애플리케이션

제외한 기능은 현재 경계를 변경하지 않고 이후 단계에서 추가할 수 있어야 합니다.

## 3. 선택한 접근 방식

### 선택: 독립 애플리케이션 4개와 공통 관측 라이브러리

```text
user-api
loan-application-api
loan-evaluation-api
loan-contract-api
observability-log (공통 라이브러리)
```

도메인별 API는 독립 프로세스와 포트를 사용합니다. `observability-log`는 로그 필드 이름과 도메인 이벤트 기록 방식만 공유하고, 업무 모델은 공유하지 않습니다.

이 방식은 모놀리스보다 서비스 호출과 분산 추적을 분명하게 보여주며, 각 서비스에 공통 코드를 복사하는 방식보다 로그 규약의 불일치를 줄입니다. 반면 배포 단위마다 데이터베이스를 두는 완전한 운영형 MSA는 데모 범위를 넘어가므로 인메모리 저장소를 사용합니다.

## 4. 모듈과 책임

| 모듈 | 외부 책임 | 내부 연동 |
|---|---|---|
| `user-api` | 휴대폰 본인인증, 신분증 진위확인 | 인증 토큰을 사용자 식별자로 해석하는 내부 API 제공 |
| `loan-application-api` | 신청 생성, 신청서 제출, 서류 제출, 신청 상태 소유 | 사용자 확인을 위해 `user-api` 호출 |
| `loan-evaluation-api` | 한도조회, 대출 심사 | 신청 유효성 확인과 상태 변경을 위해 `loan-application-api` 호출 |
| `loan-contract-api` | 약정서 작성·서명, 대출금 지급 | 신청 유효성 확인과 상태 변경을 위해 `loan-application-api` 호출 |
| `observability-log` | ECS 기반 도메인 이벤트 모델과 기록기 | Spring/MDC 컨텍스트에서 trace와 서비스 메타데이터 결합 |

서비스는 서로의 저장소나 업무 클래스를 직접 참조하지 않습니다. 서비스 간 계약은 HTTP 요청·응답 DTO로만 표현합니다.

## 5. 대출 여정과 API 흐름

### 인증 시뮬레이션

`user-api`의 휴대폰 인증 성공 응답은 데모용 opaque access token을 반환합니다. 클라이언트는 이후 요청에 `Authorization: Bearer <token>`을 전달합니다. 실제 JWT 서명이나 만료 정책은 구현하지 않습니다.

### 대표 흐름

1. `POST /api/v1/phone-verifications`
   - `phone-verification-completed`
2. `POST /api/v1/loan-applications`
   - 응답으로 `applicationId` 반환
   - `loan-application-created`
3. `POST /api/v1/loan-applications/{applicationId}/limit-inquiries`
   - `loan-offer-provided`
4. `POST /api/v1/loan-applications/{applicationId}/submit`
   - `loan-application-submitted`
5. `POST /api/v1/loan-applications/{applicationId}/identity-verifications`
   - `identity-card-verified`
6. `POST /api/v1/loan-applications/{applicationId}/documents`
   - `loan-documents-submitted`
7. `POST /api/v1/loan-applications/{applicationId}/evaluations`
   - `loan-evaluation-approved`
8. `POST /api/v1/loan-applications/{applicationId}/contracts`
   - `loan-contract-signed`
9. `POST /api/v1/loan-applications/{applicationId}/payments`
   - `loan-payment-completed`

외부 API의 URL은 업무 흐름을 읽을 수 있도록 통일하되, 실제 요청은 해당 책임을 가진 서비스 포트로 전송합니다. 평가와 계약 서비스는 작업 전에 `loan-application-api`의 내부 API로 현재 상태를 조회하고, 성공 후 상태 전이를 요청합니다.

### 클라이언트가 보관하는 값

- access token: 사용자 인증 문맥
- `applicationId`: 대출 신청이라는 업무 여정의 상관관계 키

클라이언트는 `trace.id`, `span.id`, `http.request.id`, `event.id`, `event.action`을 생성하거나 보관하지 않습니다.

## 6. 신청 상태 모델

`loan-application-api`가 다음 상태를 단독으로 소유합니다.

```text
CREATED
  -> OFFER_PROVIDED
  -> APPLICATION_SUBMITTED
  -> IDENTITY_VERIFIED
  -> DOCUMENTS_SUBMITTED
  -> EVALUATION_APPROVED
  -> CONTRACT_SIGNED
  -> PAID
```

각 상태 변경은 기대하는 이전 상태에서만 허용합니다. 순서가 잘못된 요청은 `409 Conflict`와 구조화된 문제 응답을 반환합니다. 같은 요청의 재전송은 현재 상태가 이미 목표 상태라면 성공 응답을 반환하는 멱등 동작으로 처리합니다.

## 7. 로그 규약

### 로그 데이터셋

- `journeyops.http`: 요청 결과, 지연, 상태 코드, 예외 연결
- `journeyops.domain-event`: 실제 업무 상태 변경

퍼널 계산에는 `journeyops.domain-event`만 사용합니다. API 호출 로그를 퍼널 사건으로 간주하지 않습니다.

### 도메인 이벤트 필드

```json
{
  "@timestamp": "2026-08-15T10:00:00.000Z",
  "log.level": "INFO",
  "service.name": "loan-evaluation-api",
  "event.id": "01K...",
  "event.kind": "event",
  "event.category": ["process"],
  "event.type": ["change"],
  "event.action": "loan-evaluation-approved",
  "event.outcome": "success",
  "event.dataset": "journeyops.domain-event",
  "trace.id": "...",
  "span.id": "...",
  "user.id": "sha256:...",
  "loan.application.id": "app-456"
}
```

규칙은 다음과 같습니다.

- `event.action`은 ID가 들어가지 않는 고정된 kebab-case 이름입니다.
- `event.id`는 한 업무 사건을 유일하게 식별합니다. 같은 논리 사건의 재시도에는 같은 ID를 재사용할 수 있도록 상태 변경 결과와 함께 보관합니다.
- `loan.application.id`는 신청 생성 이후 전체 퍼널을 묶는 기준입니다.
- 신청 생성 전 휴대폰 인증 사건은 가명 처리한 `user.id`로 기록합니다.
- 신청 생성 사건에는 `user.id`와 `loan.application.id`를 함께 기록해 두 구간을 연결합니다.
- `trace.id`와 `span.id`는 애플리케이션 계측에서 자동으로 생성하고 `RestClient` 호출에 전파합니다.
- DTO 전체, 요청 본문, access token, 휴대폰 번호, 주민등록번호, 신분증 정보는 로그에 기록하지 않습니다.

## 8. Elasticsearch, Kibana, Sentry

### Elasticsearch와 Kibana

애플리케이션은 JSON을 표준 출력과 공유 로그 볼륨에 기록합니다. 로컬 Filebeat가 공유 볼륨의 로그를 Elasticsearch로 전송합니다. 이 방식은 데모 환경의 재현성을 우선한 선택이며, Kubernetes 단계에서는 표준 출력 수집 방식으로 교체합니다.

Kibana에는 다음 데이터 뷰와 예시 쿼리를 문서화합니다.

- 데이터 뷰: `journeyops-*`
- 단일 신청 조회: `loan.application.id : "app-456"`
- 도메인 사건만 조회: `event.dataset : "journeyops.domain-event"`
- 오류 요청과 업무 사건 연결: 동일한 `trace.id` 검색
- 퍼널: `event.action`을 순서 조건으로 사용하고 `loan.application.id`의 고유 개수를 집계

### Sentry

각 API는 `SENTRY_DSN`이 존재할 때만 Sentry 전송을 활성화합니다. 처리되지 않은 예외와 명시적으로 포착한 외부 서비스 장애를 기록합니다.

Sentry 태그에는 `service.name`, `trace.id`, `loan.application.id`, 오류 코드를 포함할 수 있습니다. 사용자 원문 입력과 인증 토큰은 전송 전에 제거합니다. Sentry는 오류 조사에, Kibana는 전체 로그 검색과 퍼널 분석에 사용합니다.

## 9. 오류 처리

모든 서비스는 RFC 9457 Problem Details 형태를 사용합니다.

```json
{
  "type": "https://journeyops.dev/problems/invalid-application-state",
  "title": "Invalid application state",
  "status": 409,
  "detail": "The application cannot transition to the requested state.",
  "code": "INVALID_APPLICATION_STATE",
  "requestId": "..."
}
```

- 유효하지 않은 입력: `400 Bad Request`
- 인증 실패: `401 Unauthorized`
- 신청을 찾을 수 없음: `404 Not Found`
- 잘못된 상태 전이: `409 Conflict`
- 내부 `RestClient` 연결·타임아웃: `503 Service Unavailable`
- 예상하지 못한 오류: `500 Internal Server Error` 및 Sentry 보고

오류 응답에는 스택 트레이스나 내부 주소를 노출하지 않습니다.

## 10. Docker Compose 구성

```text
user-api                 : 8081
loan-application-api     : 8082
loan-evaluation-api      : 8083
loan-contract-api        : 8084
elasticsearch            : 9200
kibana                   : 5601
filebeat                 : 내부 전용
```

각 API는 다른 서비스의 URL, 로그 경로, Sentry DSN을 환경 변수로 받습니다. Elasticsearch와 Kibana는 로컬 데모에 적합한 단일 노드·보안 비활성 설정을 사용하며 운영 설정으로 재사용하지 않습니다.

## 11. 테스트 전략

구현은 테스트 우선으로 진행합니다.

- 도메인 테스트: 상태 전이, 멱등 재요청, 잘못된 순서 거부
- 컨트롤러 테스트: 상태 코드와 Problem Details 계약
- 로그 테스트: 필수 ECS 필드, 민감정보 미포함, 고정된 `event.action`
- 클라이언트 테스트: `RestClient` 성공, 404/409 전달, 연결 실패의 503 변환
- 통합 테스트: 네 서비스의 대표 happy path
- Docker smoke 테스트: Compose 기동 후 전체 여정 호출과 Elasticsearch 문서 확인

외부 시스템은 실제 통신이 필요한 경계에서만 테스트 대역을 사용합니다. 도메인 상태와 이벤트 생성은 실제 객체를 검증합니다.

## 12. 이후 확장

- Kubernetes에서는 API별 Deployment/Service, ConfigMap/Secret, readiness/liveness probe를 추가합니다.
- Filebeat 공유 파일 대신 노드 또는 사이드카 기반 표준 출력 수집으로 전환합니다.
- Kafka 도입 시 `journeyops.domain-event`의 의미와 `event.id`를 유지한 채 CloudEvents envelope로 발행합니다.
- 인메모리 저장소를 서비스별 데이터베이스로 교체하고 outbox를 추가합니다.
- 프론트엔드는 access token과 `applicationId`만 유지하며 관측용 ID 규칙에는 관여하지 않습니다.

## 13. 완료 조건

- `./gradlew check`가 성공합니다.
- `docker compose up --build`로 전체 구성이 기동됩니다.
- 제공된 smoke 스크립트가 전체 대출 여정을 완료합니다.
- Elasticsearch에서 동일한 `loan.application.id`를 가진 도메인 이벤트 8개를 시간순으로 조회할 수 있습니다. 휴대폰 인증 사건은 신청 생성 사건의 `user.id`를 통해 연결됩니다.
- 의도적으로 발생시킨 오류는 구조화 로그에 남고, DSN이 설정된 경우 Sentry에서도 확인할 수 있습니다.
