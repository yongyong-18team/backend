# 협업 규칙

2주 동안 6명이 같은 속도로 가기 위한 규칙이다. 코드 규칙은 `AGENTS.md`에 있다. AI도 같은 파일을 읽는다.

## 0. 흐름 한 줄 요약

**이슈 → 브랜치 → PR(Closes #이슈) → CI·리뷰 → Squash Merge → 서버 자동 배포**

모든 작업은 GitHub 이슈에서 시작하고, Projects 보드에서 상태를 본다. 기획 문서와 회의록은 노션에 둔다.

## 1. 처음 받았을 때

준비물은 JDK 17과 Docker Desktop이다.

```bash
git clone https://github.com/{조직}/batong-backend.git
cd batong-backend
cp .env.example .env     # 값은 노션 비공개 페이지에서
docker compose up -d db  # 로컬 PostgreSQL
./gradlew bootRun --args='--spring.profiles.active=local'
```

- http://localhost:8080/swagger-ui/index.html 이 열리면 끝.
- IntelliJ에서는 Project SDK를 17로, Lombok 플러그인과 Annotation Processing을 켠다.
- 앱이 DB 연결 에러로 안 뜨면 먼저 Docker Desktop이 켜져 있는지 본다.

## 2. 이슈

- API 하나 또는 화면 하나가 이슈 하나다. 백엔드 이슈는 이미 API ID별로 만들어 두었다.
- 제목은 `[API ID] 설명`이다. 예: `[SHARE-03] 공유 승인·링크 발급`
- 라벨
  - 담당: `backend-a` `backend-b` `backend-c`
  - 우선순위: `P0` `P1`
  - 종류: `bug` `docs` `chore`
- 작업을 시작하면 Projects 보드에서 In Progress로 옮기고 Assignee를 자기로 한다.
- 버그나 명세 질문도 이슈로 남긴다. 카톡에서 정한 건 이슈 코멘트로 한 줄 남긴다. 나중에 왜 그렇게 했는지 찾을 수 있게 하기 위해서다.

## 3. 브랜치

- `main` 하나만 쓴다. `main`은 항상 배포 가능한 상태다.
- 작업 브랜치는 최신 `main`에서 만든다. 이름은 `{type}/{이슈번호}-{짧은 설명}`이다.

```
feat/12-share-approve
fix/31-diary-analyze-timeout
chore/5-ci-cache
```

- 브랜치는 하루 이틀 안에 머지한다. 오래 들고 있을수록 충돌이 커진다.
- 작업 중에도 하루 한 번은 `main` 변경을 받아온다. 둘 중 편한 걸 쓴다.
  - PR 화면의 **Update branch** 버튼
  - 터미널: `git fetch origin && git merge origin/main`
- rebase는 쓰지 않는다. Squash Merge라서 브랜치 안에 merge 커밋이 생겨도 `main` 기록은 깔끔하다.

## 4. 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/) 형식을 쓴다.

```
<type>(<scope>): <요약>

<본문: 왜 바꿨는지. 필요할 때만>
```

**type**

| type | 언제 |
| --- | --- |
| feat | 기능 추가 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 구조 변경 |
| test | 테스트만 추가·수정 |
| docs | 문서 |
| chore | 설정, 의존성, 빌드 |
| ci | GitHub Actions, 배포 |

**scope**는 도메인 패키지 이름이다: `global` `auth` `user` `subject` `member` `diary` `analysis` `guide` `report` `share` `support` `wellbeing` `infra` `db` `ci`

**요약**은 한국어로, 50자 안쪽으로 쓴다. 무엇을 했는지 쓰고, API를 다루면 끝에 API ID를 붙인다.

```
feat(share): 공유 승인 시 토큰 발급 (SHARE-03)
fix(analysis): LLM 실패 시 키워드 적중이면 CRISIS로 저장 (DIARY-02)
test(member): 공동 관리자 삭제 시 링크 철회 테스트
chore(db): diary 조회 인덱스 추가
```

- Squash Merge라서 `main`에 남는 건 **PR 제목**이다. PR 제목이 이 형식이 아니면 CI가 막는다(`pr-title` 검사). 브랜치 안의 커밋은 조금 자유로워도 된다.
- AI가 커밋 메시지를 써도 이 형식을 지킨다.

## 5. PR

- **크기**: 이슈 하나가 PR 하나다. 변경 400줄이 넘으면 나눈다(테스트·마이그레이션 제외).
- **본문**: PR 템플릿을 채운다. `Closes #이슈번호`를 넣으면 머지할 때 이슈가 닫히고 보드가 Done으로 움직인다.
- **Draft PR**: 하루 넘게 걸리는 작업은 Draft로 먼저 열어 둔다. 방향이 틀렸으면 일찍 알 수 있다.
- **리뷰 짝**: 백엔드 A→B, B→C, C→A. 짝이 바쁘면 다른 사람도 된다. `global/`·`build.gradle`·`.github/`·`docs/`를 바꾸면 CODEOWNERS로 백엔드 A에게 리뷰가 자동 요청된다. 이 경우 A도 확인한 뒤 머지한다.
- **리뷰 속도**: 요청받으면 반나절 안에 본다. 늦어지면 짝에게 말한다.
- **머지 조건**: CI(`build`, `pr-title`) 통과, 승인 1명. 승인 후 작성자가 직접 Squash Merge 한다. 브랜치는 자동으로 지워진다.
- **리뷰에서 볼 것**: 명세와 같은지, 권한 확인이 서비스에도 있는지, 로그에 민감 정보가 없는지, 테스트가 규칙을 검증하는지. 스타일 취향은 지적하지 않는다.
- **코멘트 말머리**: 꼭 고쳐야 하면 `[필수]`, 의견이면 `[제안]`, 궁금하면 `[질문]`. `[제안]`은 반영하지 않고 머지해도 된다.

## 6. 스키마·API·공통 코드 변경

- **스키마**: Flyway 파일을 새로 추가한다. 이름은 `V{yyyyMMddHHmm}__{담당}_{설명}.sql`이다. 머지된 파일은 절대 고치지 않는다. ERD 문서는 백엔드 A가 같이 고친다.
- **API**: Swagger(어노테이션)를 먼저 고치고, 맞붙는 프론트에게 알린 다음 구현한다. 응답 필드 이름은 프론트와 합의한 뒤에만 바꾼다.
- **global**: 백엔드 A만 수정한다. 에러 코드나 공용 enum이 필요하면 이슈나 PR 코멘트로 요청한다.
- **의존성**: `build.gradle`는 백엔드 A만 수정한다. 라이브러리가 필요하면 이유와 함께 요청한다.

## 7. AI 활용

각자 Claude Code, Cursor, Copilot, ChatGPT 등 원하는 도구를 쓰면 된다. 대신 아래를 지킨다.

**맥락은 레포에 있다**
- `AGENTS.md`가 AI용 규칙 파일이다. Cursor·Copilot·Codex는 이 파일을 바로 읽고, Claude Code는 `CLAUDE.md`를 거쳐 같은 내용을 읽는다.
- 웹 채팅(ChatGPT 등)을 쓸 때는 `AGENTS.md`와 해당 API 명세 부분을 같이 붙여 넣는다.
- AI 규칙이 부족하다고 느끼면 `AGENTS.md` 수정 PR을 올린다. 같은 실수를 두 번 봤을 때가 추가할 때다.

**요청은 이렇게**

```
#12 이슈, SHARE-03 공유 승인 API를 구현해줘.
- 명세: docs/API_명세_v1.4.md의 SHARE-03, 테이블은 docs/ERD_v1.4.md의 share
- 범위: com.batong.share 패키지만. global이 필요하면 수정하지 말고 알려줘
- 완료 기준: 요청·응답·에러 코드가 명세와 같을 것, 서비스 단위 테스트 통과
먼저 계획을 보여주고, 내가 확인하면 구현해줘.
```

이슈 번호와 API ID, 문서 위치, 수정 범위, 완료 기준을 넣으면 엉뚱한 코드가 거의 안 나온다.

**작업 습관**
- **계획 먼저**: 바로 코드를 쓰게 하지 말고 바꿀 파일 목록과 계획부터 받는다.
- **작업 하나에 대화 하나**: 다른 이슈로 넘어가면 새 대화로 시작한다. 이전 맥락이 섞이면 엉뚱한 파일을 고친다.
- **직접 읽고 직접 실행**: AI가 쓴 코드도 내 코드다. 리뷰 질문에 "AI가 짰어요"는 답이 아니다. 실행해 보고 Swagger로 호출해 본다.
- **테스트를 고쳐서 통과시키는지 본다**: AI가 기대값을 바꿔서 초록불을 만드는 경우가 있다.
- **명세에 없는 걸 만들면 지운다**: 필드, 에러 코드, API, 라이브러리를 AI가 추가했으면 되돌리고 필요하면 팀에 묻는다.
- **버전 확인**: 우리는 Spring Boot 3.5, Java 17이다. `javax.*`, `WebSecurityConfigurerAdapter`, Boot 4 코드가 나오면 `AGENTS.md`의 "버전 주의"를 보여주고 다시 시킨다.

**넣지 말 것**
- `.env`, API 키, 비밀번호, 실제 사람 정보
- 다른 사람 담당 패키지 수정 요청

**리뷰에도 쓴다**
- PR diff를 AI에게 주고 "API 명세(ID)와 다른 점, 권한 누락, 로그에 남는 민감 정보"를 찾게 하면 좋다. 승인 판단은 사람이 한다.

## 8. 공개 레포 규칙

이 레포는 Public이다. 코드, 커밋, 이슈, PR 코멘트, Actions 로그까지 누구나 볼 수 있다.

**올리지 않는 것**
- `.env`, API 키, 비밀번호, 서버 IP와 SSH 키, DB 접속 정보
- 실제 사람의 이름·전화번호·주소. 팀원 본인과 가족 정보도 포함. 테스트·시드·평가셋 데이터는 지어낸 것만 쓰고, 전화번호는 `010-0000-0000` 형식으로
- 에러 로그를 이슈에 붙일 때 토큰·비밀번호·일기 본문은 지우고 붙인다
- 기획서·PRD 원본. 레포에는 개발 문서만 둔다

**커밋 전에**
- `git status`, `git diff --staged`로 올라가는 파일을 한 번 본다. `git add .` 전에 특히
- GitHub 이메일 비공개 설정을 켜고 noreply 주소로 커밋한다(커밋 이메일은 공개된다)

**키를 실수로 올렸다면**
1. 바로 단톡에 알린다
2. 그 키를 발급한 곳에서 **즉시 폐기·재발급**한다. 커밋을 지우거나 force push 해도 이미 복사됐다고 본다
3. 새 키는 `.env`와 GitHub Secrets에만 넣는다

Push protection이 켜져 있어서 알려진 형식의 키는 push 단계에서 막힌다. 막혔으면 우회하지 말고 커밋에서 키를 빼고 다시 커밋한다.

## 9. 절대 하지 말 것

- `main`에 직접 push, force push (브랜치 규칙으로도 막혀 있다)
- 비밀값·실제 개인정보 커밋 (8장)
- 머지된 Flyway 파일 수정
- Push protection 경고 우회

## 10. 자주 막히는 곳

| 증상 | 해결 |
| --- | --- |
| 앱이 `Connection refused`로 안 뜸 | Docker Desktop 켜기 → `docker compose up -d db` |
| Flyway `Validate failed` / `Detected applied migration not resolved locally` | 다른 브랜치의 마이그레이션이 로컬 DB에 남은 것. 로컬 DB 초기화: `docker compose down -v && docker compose up -d db` |
| Flyway `checksum mismatch` | 머지된 마이그레이션 파일이 바뀐 것. 고친 파일을 되돌리고 새 파일로 다시 만든다. dev 서버에서 나면 백엔드 A에게 |
| `Schema-validation: missing column` | 엔티티만 고치고 마이그레이션을 안 만든 것 |
| 테스트가 Docker 관련 에러 | Docker Desktop 켜기. 그래도 안 되면 단위 테스트만: `./gradlew test --tests "*ServiceTest"` |
| CI는 빨간데 로컬은 통과 | Actions 로그의 실패 테스트 이름 확인. main CI가 깨졌으면 백엔드 A가 먼저 고친다 |

## 11. 막혔을 때

- 30분 넘게 막히면 혼자 붙잡지 말고 단톡에 올린다. 무엇을 하려는지, 무엇을 해봤는지, 에러 메시지를 같이 올린다.
- 명세가 이상하거나 모호하면 이슈에 `docs` 라벨로 남기고 백엔드 A를 멘션한다. 임의로 정하지 않는다. 정해진 내용은 문서에 반영한다.
