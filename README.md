# spring-boot-multi-module-template

스프링 부트 멀티모듈 템플릿 프로젝트

## 기술 스택

- **Language:** Kotlin 2.2.21
- **Framework:** Spring Boot 4.0.3
- **Build:** Gradle 9.4.0 (Kotlin DSL)
- **Java:** 21
- **Test:** JUnit 5 (spring-boot-starter-test)
- **Lint:** ktlint

## 빌드 명령어

```bash
./gradlew build              # 전체 모듈 빌드 및 테스트
./gradlew check              # 전체 검사 실행 (테스트 + ktlint)
./gradlew bootRun            # Spring Boot 애플리케이션 실행
./gradlew bootJar            # 실행 가능한 jar 빌드

# 테스트
./gradlew test               # 전체 테스트 실행
./gradlew :demo:test         # 특정 모듈 테스트 실행
./gradlew test --tests "fully.qualified.TestClass"  # 단일 테스트 클래스 실행

# 린트 (ktlint)
./gradlew ktlintCheck        # 코드 스타일 검사
./gradlew ktlintFormat       # 코드 자동 포맷팅
```

## 아키텍처

### 컨벤션 플러그인 시스템 (buildSrc)

빌드 로직은 개별 모듈의 build 파일이 아닌 `buildSrc/src/main/kotlin/`에 컨벤션 플러그인으로 중앙 집중화되어 있다.

| 플러그인 | 설명 |
|---|---|
| `global-convention` | 모든 모듈에 적용. Kotlin JVM, ktlint, Java 21 툴체인, kotlin-logging, JUnit 5, 저장소 설정 |
| `spring-boot-convention` | Spring Boot 플러그인, dependency-management 플러그인, Kotlin Spring 플러그인(all-open) 적용. bootJar로 실행 가능한 앱 빌드 |
| `spring-jar-convention` | Spring Boot BOM을 `platform()`으로 가져오는 라이브러리 모듈용. bootJar 없이 일반 jar로 빌드 |

### 새 모듈 추가 방법

1. 프로젝트 루트에 새 디렉토리 생성
2. 적절한 컨벤션 플러그인을 적용하는 `build.gradle.kts` 추가
3. `settings.gradle.kts`에 `include()`로 모듈 등록
4. Kotlin, 린트, 테스트, 의존성 관리가 자동으로 상속됨

### 버전 관리

모든 의존성 버전은 `gradle/libs.versions.toml`(Gradle 버전 카탈로그)에서 중앙 관리.

## CI

GitHub Actions에서 JDK 21(Amazon Corretto) 환경으로 main/dev push와 PR에 `./gradlew check` 실행.

- `org.gradle.caching=true`로 Gradle 빌드 캐시 활성화
- merge-base 기반 캐싱 전략으로 증분 빌드 최적화
