# 레포 세팅 순서 (백엔드 A용, 끝나면 이 파일은 지운다)

이 폴더 자체가 Spring Boot 3.5.16 프로젝트다(Gradle 래퍼 포함). Spring Initializr로 따로 만들 필요 없다.

## 0. 시작 전 확인

- **대회 규정**: 저장소 공개 여부와 사전 개발 허용 범위를 먼저 본다. 비공개로 해야 하면 1번의 "Private로 해야 할 때"를 따른다.
- **팀원 전원 이메일 비공개 설정**: Public 레포에서는 커밋에 들어간 이메일이 누구에게나 보인다.
  - GitHub → Settings → Emails → **Keep my email addresses private**, **Block command line pushes that expose my email** 켜기
  - 로컬 git에는 GitHub가 주는 noreply 주소를 쓴다: `git config --global user.email "{숫자}+{아이디}@users.noreply.github.com"`

## 1. GitHub 조직과 레포

1. GitHub 조직(Organization)을 만든다. 무료 플랜이면 된다. 예: `batong-team`
2. `batong-backend` 레포를 **Public**, 빈 레포로 만든다(README·.gitignore 추가 안 함). 프론트 레포는 프론트가 만든다.
   - 무료 조직의 Private 레포에는 브랜치 보호 규칙이 걸리지 않는다. 키는 어차피 커밋하지 않으니 Public이 낫고, 포트폴리오로 바로 보여줄 수 있다.
   - **Private로 해야 할 때**: GitHub Student Developer Pack으로 Pro가 된 개인 계정 아래에 Private 레포를 만들고 팀원을 Collaborator로 초대한다. Pro 개인 계정의 Private 레포에서는 브랜치 규칙이 동작한다.
3. 팀원을 초대하고 레포에 Write 권한을 준다. Admin은 백엔드 A만.

## 2. 프로젝트 올리기

1. 이 폴더를 원하는 위치에 풀고 이름을 `batong-backend`로 바꾼다.
2. `.github/CODEOWNERS`의 `@BACKEND_A`를 내 GitHub 아이디로 바꾼다.
3. 로컬에서 동작 확인

```bash
docker compose up -d db
./gradlew build                                              # 테스트 포함, 처음엔 의존성 내려받느라 몇 분 걸림
./gradlew bootRun --args='--spring.profiles.active=local'    # Swagger 열리면 성공
```

4. 첫 커밋은 `main`에 바로 push 한다(브랜치 규칙을 걸기 전). **커밋 전에 `git status`로 `.env`가 없는지 본다.**

```bash
git init -b main
git add .
git status
git commit -m "chore(ci): 프로젝트 초기 설정"
git remote add origin https://github.com/batong-team/batong-backend.git
git push -u origin main
```

`./gradlew build`가 의존성 문제로 실패하면 에러 메시지를 그대로 공유한다. 이 묶음은 빌드를 돌려보지 못한 상태로 만들었다.

## 3. 레포 설정 (Settings)

**General → Pull Requests**
- Allow merge commits: 끔
- Allow squash merging: 켬, Default commit message는 **Pull request title**
- Allow rebase merging: 끔
- Always suggest updating pull request branches: 켬 (PR 화면에 Update branch 버튼)
- Automatically delete head branches: 켬

**Rules → Rulesets → New branch ruleset** (대상: Default branch, Enforcement: Active)
- Restrict deletions, Block force pushes
- Require linear history
- Require a pull request before merging: 승인 1명
  - **Require review from Code Owners는 켜지 않는다.** 코드 오너가 백엔드 A 한 명뿐이라 A가 올린 `global/` PR을 아무도 승인할 수 없게 된다. CODEOWNERS는 리뷰 자동 요청용으로만 쓴다.
- Require status checks to pass: `build`, `pr-title` (첫 PR에서 CI가 한 번 돈 뒤에 목록에 나타난다)
- Bypass list는 비워 둔다. 관리자도 PR로 머지한다.

**Code security** (Public 레포는 무료)
- Secret scanning, **Push protection** 켜기: 키가 들어간 push를 GitHub가 막는다.
- Dependabot alerts 켜기. 버전 자동 업데이트 PR은 2주 동안 소음만 늘어서 켜지 않는다.

**Actions → General**
- Workflow permissions: **Read repository contents** (기본 읽기 전용)

배포용 Secrets·Variables는 서버를 만들 때 넣는다(`DEPLOY.md` 2-6). 그 전까지 CI는 빌드·테스트만 한다.

## 4. 이슈와 보드

1. 조직의 Projects에서 보드를 하나 만든다. 이름: `돌봄 바통`
   - 컬럼: Todo / In Progress / In Review / Done
   - Workflows에서 "Item closed → Done", "Pull request merged → Done"이 켜져 있는지 본다
2. 백엔드 이슈를 한 번에 만든다. 한 번만 실행한다(다시 돌리면 중복 생성).

```bash
gh auth login
gh auth refresh -s project
./scripts/create-issues.sh batong-team/batong-backend "돌봄 바통"
```

백엔드 이슈 37개와 라벨이 만들어진다. 프론트가 원하면 앞에 `FRONTEND_REPO=batong-team/batong-frontend`를 붙여 화면별 이슈 17개도 만들 수 있다.

## 5. 팀에 공지

- 레포 주소, `CONTRIBUTING.md` 먼저 읽기 (특히 8장 공개 레포 규칙, 10장 자주 막히는 곳)
- 이메일 비공개 설정 (0번)
- `.env` 값은 노션 비공개 페이지에
- JDK 17, Docker Desktop 설치 후 `docker run hello-world`까지 확인

## 6. 서버 (D2~D4)

`DEPLOY.md` 2장 순서대로 한다. 마감은 D4. 그 전까지 프론트는 로컬 백엔드나 API 명세 예시 JSON으로 작업한다.
배포를 한 번은 백업 담당(백엔드 C)과 같이 해 본다.
