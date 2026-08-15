# JourneyOps

대출 신청 여정을 실행하면서 Sentry와 Elastic Stack의 역할을 분리해 체험하는 Spring Boot 데모입니다. 애플리케이션은 예상 밖의 예외를 Sentry로 보내고, Filebeat·Elasticsearch·Kibana는 구조화 로그 검색, 서비스 상태, 단일 신청 타임라인, 사용자 퍼널을 담당합니다.

## 구성

| 서비스 | 포트 | 책임 |
|---|---:|---|
| `user-api` | 8081 | 휴대폰 본인인증, 사용자 조회, 신분증 진위확인 |
| `loan-application-api` | 8082 | 신청 생성, 상태 전이, 신청서·서류 제출 |
| `loan-evaluation-api` | 8083 | 한도조회, 대출 심사 |
| `loan-contract-api` | 8084 | 약정서 작성, 대출금 지급 |
| Elasticsearch | 9200 | ECS 로그 저장, ILM, 2차 마스킹 |
| Kibana | 5601 | Discover, 대시보드, query rule |

서비스 간 호출은 Spring `RestClient`를 사용합니다. `loan-application-api`가 신청 상태의 단일 소유자이며 다른 서비스는 상태 전이 API를 호출합니다.

## 바로 실행

필수 도구는 Docker, Docker Compose, `curl`, `jq`입니다.

```bash
docker compose -f docker/compose.yml up -d --build
bash docker/kibana/setup.sh
bash scripts/smoke.sh
```

성공하면 다음 메시지가 출력됩니다.

```text
Journey completed: <applicationId>, 8 application events indexed
```

Kibana는 [http://localhost:5601](http://localhost:5601)에서 열 수 있습니다. `JourneyOps - Loan Journey` 대시보드 또는 다섯 개의 Saved Discover session으로 로그를 탐색하세요.

## 선택적 Sentry 연동

DSN이 없어도 모든 API는 정상 기동합니다. Sentry 프로젝트를 연결하려면 Compose 실행 전에 환경 변수를 설정합니다.

```bash
export SENTRY_DSN='https://public@example.ingest.sentry.io/project-id'
docker compose -f docker/compose.yml up -d --build
```

예상 밖의 500 오류 경로는 다음과 같이 확인할 수 있습니다.

```bash
curl -sS -X POST http://localhost:8081/api/v1/demo/failures
```

Sentry 전송 전 휴대폰 번호, 주민등록번호, 이메일, 카드번호, Bearer token, JWT를 마스킹하며 request body와 인증·쿠키 헤더는 전송하지 않습니다.

## 로그 규약

- 도메인 퍼널은 API URL이 아니라 안정적인 `event.action`으로 집계합니다.
- 신청 이후 여덟 사건은 `loan.application.id`로 묶습니다.
- 신청 전 휴대폰 인증은 가명화한 `user.id`로 연결합니다.
- 임의 MDC는 `custom.*` 아래로 들어가며 예약 ECS 필드를 덮어쓰지 않습니다.
- 장애 조사는 `trace.id`로 서비스 간 요청을 연결합니다.

자세한 사용법은 [Kibana 퍼널 가이드](docs/kibana-funnel.md)와 [로그 분석 가이드](docs/log-analytics.md)를 참고하세요.

## 로컬 빌드와 테스트

```bash
./gradlew check
./gradlew bootJar
```

## 종료

아래 명령은 컨테이너와 데모용 Elasticsearch·Filebeat 볼륨을 함께 제거합니다.

```bash
docker compose -f docker/compose.yml down -v
```

Elastic 보안 비활성화와 고정 암호화 키는 로컬 데모 전용입니다. Kubernetes로 옮길 때는 애플리케이션 이미지는 그대로 사용하고 로그 수집을 DaemonSet/Elastic Agent로, 비밀값을 Secret으로 분리할 수 있습니다.
