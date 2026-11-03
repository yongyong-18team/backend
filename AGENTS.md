# 돌봄 바통 백엔드

Spring Boot 3.5.16 / Java 17 / PostgreSQL 16 / Gradle(Groovy). 해커톤 MVP, 가상 데이터만 사용.

## Commands
- 로컬 DB: `docker compose up -d db`
- 실행: `./gradlew bootRun --args='--spring.profiles.active=local'`
- 전체 테스트: `./gradlew test` (Testcontainers 테스트가 있어 Docker 필요)
- 테스트 하나: `./gradlew test --tests "com.batong.share.ShareServiceTest"`
- 빌드: `./gradlew build`
- 로컬 DB 초기화: `docker compose down -v && docker compose up -d db`
- Swagger: http://localhost:8080/swagger-ui/index.html

## 기준 문서
- 구현 기준은 `docs/`의 개발 문서 v1.4이다. PRD와 다르면 개발 문서를 따른다.
- 테이블·컬럼은 `docs/ERD_v1.4.md`, 요청·응답·에러 코드는 `docs/API_명세_v1.4.md`를 그대로 쓴다.
- 명세에 없는 필드, API, 에러 코드, 라이브러리를 새로 만들지 않는다. 명세가 모호하면 구현하지 말고 무엇이 모호한지 말한다.
- PRD에 남은 옛 문구(3.2 공개 범위, 6.2 권한 선택, 수신자 유형, 유효기간 선택)는 구현하지 않는다. 목록은 `docs/TRD_v1.4.md` 16-1.

## 담당과 수정 범위
- 백엔드 A: global, auth, user, member, guide, share, 인프라
- 백엔드 B: subject, diary, analysis, infra.llm, infra.stt
- 백엔드 C: report, support, wellbeing, infra.publicdata, 스케줄러
- `global/`과 `build.gradle`은 백엔드 A만 수정한다. 필요하면 수정하지 말고 요청할 내용을 정리해 준다.
- 요청받은 도메인 패키지 밖의 코드는 고치지 않는다.
- 새 도메인을 만들 때는 이미 있는 도메인 패키지 하나를 열어 같은 구조와 이름 짓기를 따른다.

## Conventions
- 패키지: `com.batong.{도메인}.{controller|service|repository|domain|dto}`
- 응답은 `ApiResponse<T>`로 감싸고, 에러는 `BusinessException(ErrorCode.XXX)`로 던진다.
- 엔티티에 setter를 두지 않는다. 상태 변경은 의미 있는 메서드로 한다. 기본 생성자는 `protected`.
- DTO는 record. 엔티티를 응답으로 그대로 내보내지 않는다.
- 시각은 `Instant`, 날짜는 `LocalDate`. `LocalDateTime`은 쓰지 않는다.
- 권한은 컨트롤러 `@RequireRole`과 서비스 계층 양쪽에서 확인한다.
- 섹션·category·상태값은 공용 enum만 쓴다. 문자열 상수를 새로 만들지 않는다.
- 외부 HTTP 호출(LLM, STT, 공공데이터)은 `RestClient`로 한다. `RestTemplate`, `WebClient`는 쓰지 않는다.

## 버전 주의 (Spring Boot 3.5)
- `jakarta.*`를 쓰고 `javax.*`는 쓰지 않는다.
- 보안 설정은 `SecurityFilterChain` 빈과 람다 DSL로 한다. `WebSecurityConfigurerAdapter`, `.and()` 체이닝은 쓰지 않는다.
- 테스트 목은 `@MockitoBean`을 쓴다(`@MockBean`은 3.4부터 deprecated).
- JWT는 jjwt 0.12 API로 쓴다(`Jwts.builder()...signWith(key)`, `Jwts.parser().verifyWith(key)`). `SignatureAlgorithm`을 넘기는 옛 방식은 쓰지 않는다.
- Spring Boot 4 전용 API, starter 이름(`spring-boot-starter-webmvc` 등), `tools.jackson` 패키지를 쓰지 않는다.

## Gotchas
- 스키마는 Flyway로만 바꾼다. `ddl-auto=validate`라서 엔티티만 고치면 앱이 뜨지 않는다. 규칙은 `.claude/rules/migration.md`.
- 공유 토큰은 SHA-256 해시만 저장한다. 원문 토큰을 DB나 로그에 남기지 않는다.
- 로그에 일기 본문, LLM 요청·응답, 비밀번호, 토큰을 남기지 않는다.
- `wellbeing_signal`과 `ai_analysis.raw_json`은 어떤 API 응답에도 넣지 않는다.
- 공동 관리자에게는 일기 원문을 주지 않는다.
- AI 분석 코드(analysis, infra.llm, prompts)를 다룰 때는 `.claude/rules/ai-safety.md`를 먼저 읽는다.
- 로컬과 CI는 `AI_MODE=mock`. 실제 키는 `.env`에만 둔다.
- 비밀값은 코드·yml·테스트에 쓰지 않는다. local 외 프로필에서 `JWT_SECRET`이 비어 있거나 32바이트 미만이면 기동을 실패시킨다.
- `ADMIN_API_KEY`가 비어 있으면 관리자 API는 항상 거부한다. 빈 헤더와 빈 키를 같다고 보면 안 된다. 키 비교는 `MessageDigest.isEqual`로 한다.
- 테스트·시드 데이터는 지어낸 것만 쓴다. 이름은 가상 이름, 전화번호는 `010-0000-0000` 형식. 이 레포는 공개 저장소다.

## Git
- 커밋·PR 형식은 `CONTRIBUTING.md`를 따른다. 이 레포에서는 커밋 요약을 한국어로 쓴다.
- 커밋과 푸시는 요청받았을 때만 한다. `main`에 직접 커밋하지 않는다. rebase·force push 하지 않는다.

## 작업 방식
- 기능 구현은 먼저 계획(바꿀 파일, 명세와 다른 점)을 보여주고 확인을 받은 뒤 진행한다.
- 테스트가 실패하면 테스트 기대값을 바꿔서 통과시키지 않는다. 실패 원인을 먼저 보고한다.
- 의존성 추가가 필요하면 추가하지 말고 이유와 함께 제안한다.
