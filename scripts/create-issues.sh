#!/usr/bin/env bash
# 개발 문서 v1.4 기준으로 라벨과 이슈를 한 번에 만든다. 한 번만 실행한다.
# 사용법: ./scripts/create-issues.sh {조직}/batong-backend ["프로젝트 보드 이름"]
# 프론트 이슈도 만들려면: FRONTEND_REPO={조직}/batong-frontend ./scripts/create-issues.sh ...
# 준비: gh auth login, 프로젝트에 넣으려면 gh auth refresh -s project
set -euo pipefail

BACKEND_REPO="${1:?백엔드 레포를 넣어 주세요 (예: batong-team/batong-backend)}"
PROJECT="${2:-}"
FRONTEND_REPO="${FRONTEND_REPO:-}"

create_labels() {
  local repo="$1"
  gh label create backend-a  --repo "$repo" --color 1D76DB --description "백엔드 A" --force
  gh label create backend-b  --repo "$repo" --color 0E8A16 --description "백엔드 B" --force
  gh label create backend-c  --repo "$repo" --color FBCA04 --description "백엔드 C" --force
  gh label create frontend-a --repo "$repo" --color 5319E7 --description "프론트 A" --force
  gh label create frontend-b --repo "$repo" --color D93F0B --description "프론트 B" --force
  gh label create P0         --repo "$repo" --color B60205 --description "MVP 필수" --force
  gh label create P1         --repo "$repo" --color C5DEF5 --description "P0 끝난 뒤" --force
  gh label create docs       --repo "$repo" --color 0075CA --description "문서·명세 질문" --force
  gh label create chore      --repo "$repo" --color EDEDED --description "설정·인프라" --force
}

create_issue() {
  local repo="$1" title="$2" labels="$3" spec="$4"
  local body
  body=$(printf '## 명세 위치\n%s\n\n## 완료 기준\n- [ ] 명세와 같게 동작\n- [ ] 규칙이 있는 곳은 테스트\n- [ ] dev 환경에서 확인\n' "$spec")
  local args=(--repo "$repo" --title "$title" --label "$labels" --body "$body")
  if [[ -n "$PROJECT" ]]; then args+=(--project "$PROJECT"); fi
  gh issue create "${args[@]}"
  sleep 1
}

create_labels "$BACKEND_REPO"

# 형식: 제목|라벨|명세 위치
while IFS='|' read -r title labels spec; do
  [[ -z "$title" || "$title" == \#* ]] && continue
  create_issue "$BACKEND_REPO" "$title" "$labels" "$spec"
done <<'EOF'
# 백엔드 A
[SETUP] 프로젝트 골격, global 공통 모듈, @RequireRole 껍데기|backend-a,P0,chore|TRD 5장, 15장
[SETUP] Flyway V1 전체 스키마|backend-a,P0,chore|ERD v1.4 전체
[SETUP] CI, Lightsail 서버·PostgreSQL·Nginx·HTTPS, 도메인, 자동 배포|backend-a,P0,chore|TRD 12장, DEPLOY.md
[SETUP] 역할 판정(OWNER/CO) 실제 구현|backend-a,P0|TRD 7-2
[AUTH-01~03] 회원가입·ID 확인·로그인, JWT|backend-a,P0|API 명세 AUTH-01~03, TRD 7-1
[USER-01~03] 내 정보 조회·수정·탈퇴|backend-a,P0|API 명세 USER-01~03
[TERMS-01] 동의 항목 조회|backend-a,P0|API 명세 TERMS-01
[HOME-01] 홈 화면 데이터|backend-a,P0|API 명세 HOME-01
[GUIDE-01~04] 설명서 조회·항목 추가·수정·삭제|backend-a,P0|API 명세 GUIDE-01~04, ERD guide_entry
[ITEM-01] 추출 항목 승인 → 설명서 반영|backend-a,P0|API 명세 ITEM-01
[SHARE-01~02] 공개 섹션 옵션, 공유 초안·미리보기|backend-a,P0|API 명세 SHARE-01·02, ERD share
[SHARE-03] 공유 승인·링크 발급|backend-a,P0|API 명세 SHARE-03, TRD 7-3
[SHARE-04~05] 공유 내역, 접근 중지|backend-a,P0|API 명세 SHARE-04·05
[PUB-01] 수신자 열람|backend-a,P0|API 명세 PUB-01, TRD 7-3
[MEMBER-01~03] 계정 확인, 공동 관리자 목록·삭제|backend-a,P0|API 명세 MEMBER-01~03
[INVITE-01~03] 초대 발송·목록·취소|backend-a,P0|API 명세 INVITE-01~03
[INVITE-04~06] 받은 초대 목록·수락·거절|backend-a,P0|API 명세 INVITE-04~06
[ADMIN-02] AI_MODE 런타임 전환|backend-a,P1|API 명세 ADMIN-02
# 백엔드 B
[SETUP] LlmClient·SttClient 인터페이스와 Mock|backend-b,P0,chore|TRD 8-7, AI 명세 8장
[SETUP] LLM·STT 공급자 비교·결정|backend-b,P0,docs|TRD 8-2, 8-6
[ONB-01] 온보딩 일괄 등록|backend-b,P0|API 명세 ONB-01
[SUBJ-02~03] 대상자 조회·수정|backend-b,P0|API 명세 SUBJ-02·03
[STT-01] 음성 변환|backend-b,P0|API 명세 STT-01, TRD 8-2
[DIARY-01] 일기 저장|backend-b,P0|API 명세 DIARY-01
[DIARY-02] AI 분석 (위기 판정, 마스킹, 근거 검증)|backend-b,P0|API 명세 DIARY-02, TRD 8-3~8-5, AI 명세 3장
[DIARY-03~05] 일기 목록·상세·삭제|backend-b,P0|API 명세 DIARY-03~05
[AI] 평가셋 30건 적용, 프롬프트 조정|backend-b,P0|AI 명세 6장
# 백엔드 C
[SETUP] 공공데이터 확인·적재 방식 결정|backend-c,P0,docs|TRD 9장
[ADMIN-01] 공공데이터 적재·갱신|backend-c,P0|API 명세 ADMIN-01, TRD 9-2
[SEED] 제도 규칙표·제도-시설 매핑 시드|backend-c,P0|TRD 6장, 9-1
[RES-01~02] 돌봄 지원 조건 검색·상세|backend-c,P0|API 명세 RES-01·02, TRD 9-3
[SUP-01~02] 소진·위기 안내|backend-c,P0|API 명세 SUP-01·02, AI 명세 5장
[WELLBEING] 소진 신호 저장·누적 판정, WellbeingService.record()|backend-c,P0|AI 명세 3-6
[REPORT-01~02] 일일 기록|backend-c,P0|API 명세 REPORT-01·02
[SCHEDULER] 초안 정리·고정본 파기·분석 상태 복구|backend-c,P0|TRD 12-6
[SEED] 데모 시드 데이터|backend-c,P0|TRD 6장
[REPORT-03] 주간 리포트|backend-c,P1|API 명세 REPORT-03
EOF

if [[ -z "$FRONTEND_REPO" ]]; then
  echo "완료 (백엔드만)"
  exit 0
fi

create_labels "$FRONTEND_REPO"
while IFS='|' read -r title labels spec; do
  [[ -z "$title" || "$title" == \#* ]] && continue
  create_issue "$FRONTEND_REPO" "$title" "$labels" "$spec"
done <<'EOF'
# 프론트 A
[SETUP] 프로젝트·라우팅·API 클라이언트·Vercel·manifest|frontend-a,P0,chore|TRD 3-3, API 명세 1장
[0.1] 로그인·가입|frontend-a,P0|PRD 0.1, AUTH-01~03
[0.2] 온보딩 0.2-1~0.2-4|frontend-a,P0|PRD 0.2-1~0.2-4, ONB-01
[1.1] 홈|frontend-a,P0|PRD 1.1, HOME-01
[2.1] 일기 작성·녹음|frontend-a,P0|PRD 2.1, STT-01, DIARY-01
[2.2] AI 분석 결과 분기·소진 안내 카드|frontend-a,P0|PRD 2.2, DIARY-02
[2.3] 검토·승인|frontend-a,P0|PRD 2.3, ITEM-01
[2.4] 일기 목록·상세|frontend-a,P0|PRD 2.4, DIARY-03·04, REPORT-01·02
[5.5] 위기 안전 안내|frontend-a,P0|PRD 5.5, SUP-02
[2.5] 주간 리포트|frontend-a,P1|PRD 2.5, REPORT-03
# 프론트 B
[SETUP] 디자인 토큰·공통 컴포넌트·하단 내비게이션|frontend-b,P0,chore|PRD 5-5
[3.1~3.2] 설명서 보기·편집|frontend-b,P0|PRD 3.1·3.2, GUIDE-01~04
[4.1~4.3] 공유 만들기·미리보기·링크 발급·내역|frontend-b,P0|PRD 4.1~4.3, SHARE-01~05
[4.4] 수신자 페이지|frontend-b,P0|PRD 4.4, PUB-01
[5.0~5.3] 돌봄 지원 메뉴·조건·결과·상세|frontend-b,P0|PRD 5.0~5.3, RES-01·02
[5.4] 소진 지원 안내|frontend-b,P0|PRD 5.4, SUP-01
[6.1~6.3] 계정·초대·받은 초대·공동 관리자 삭제|frontend-b,P0|PRD 6.1~6.3, MEMBER, INVITE
EOF

echo "완료"
