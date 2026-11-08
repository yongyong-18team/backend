# 돌봄 바통 API 명세 v1.4

## 0. 문서 정보

| 항목 | 내용 |
|---|---|
| 작성자 | 백엔드 A |
| 기준 문서 | PRD(2026-11-03 수정본), 와이어프레임, 기획서, ERD v1.4, TRD v1.4, AI 명세 v1.4 |
| 운영 | 이 문서로 합의한 뒤 Swagger(springdoc)로 옮긴다. 이후 Swagger가 원본이다 |

### 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| 0.9 | 2026-11-01 | 초안 |
| 1.0 | 2026-11-01 | TRD v1.0 결정 반영 |
| 1.1 | 2026-11-02 | 팀 댓글·와이어프레임 반영 |
| 1.2 | 2026-11-03 | PRD 수정본 반영. 온보딩 일괄 등록(ONB-01 하나로 통합), 보호자 프로필(USER-02), 대상자 필드 변경, 설명서 섹션 7종 재정의, 공유 수신자 유형 삭제·비상 연락처 표시 여부·유효기간 7일 고정, 주간 리포트 최근 7일·일별 추이·주요 변화, 돌봄 지원 조건 검색(RES-02) 신설, GAP API는 P1로 이동, 6.3·6.2.1 응답 필드 보강 |
| 1.3 | 2026-11-03 | 플랫폼 React 모바일 웹 확정. STT-01 상시 사용(조건부 문구 삭제), 받는 녹음 형식에 Safari `audio/mp4` 추가, 웹 클라이언트 규약(토큰 저장, 401 처리, CORS, 수신자 페이지 경로) 추가 |
| 1.4 | 2026-11-03 | 서버 구성 변경(Lightsail)에 맞춰 문서 버전만 맞춤. API 변경 없음 |
| 1.4.1 | 2026-11-07 | REPORT-01·ADMIN-01 응답 예시 추가, RES-02 시설 0건 규칙, DIARY-02 일반 예시의 `itemCount`와 항목 수 일치 (#58) |

---

## 1. 공통 규약

- Base URL: `/api/v1`
- 인증: `Authorization: Bearer {accessToken}`. 7일, Refresh 없음. 로그아웃은 클라이언트에서 토큰 삭제
- 권한 표기: 공개 / 로그인 / OWNER / CO. `/subjects/{subjectId}/**` 경로는 서버가 역할을 판정한다(TRD 7-2).
- 공통 에러 처리: 입력 검증 실패와 지원하지 않는 메서드·Content-Type은 `COMMON_VALIDATION`, 없는 경로는 `COMMON_NOT_FOUND`, 예상하지 못한 서버 오류는 `COMMON_INTERNAL_ERROR`. 업로드 크기 초과는 `STT_FILE_TOO_LARGE`. 관리자 API(`X-Admin-Key`) 거부는 `COMMON_FORBIDDEN`
- 응답 형식
```json
{ "success": true, "data": { }, "error": null }
{ "success": false, "data": null, "error": { "code": "SHARE_EXPIRED", "message": "유효기간이 지난 링크예요." } }
```
- JSON camelCase, 날짜 `yyyy-MM-dd`, 시각 ISO-8601(+09:00)
- enum 값은 ERD v1.4를 따른다.
- 클라이언트: React 모바일 웹(TRD 3-3)
  - 토큰은 `localStorage`에 저장하고 요청마다 `Authorization` 헤더로 보낸다. 쿠키는 쓰지 않는다
  - 401을 받으면 토큰을 지우고 0.1로 이동
  - 서버 CORS는 운영 도메인, localhost, Vercel 프리뷰 도메인을 허용한다
  - 수신자 페이지는 같은 웹앱의 `/s/{token}`이고, 여기서 PUB-01을 부른다

---

## 2. 엔드포인트 목록

### 2-1. 인증·사용자 (백엔드 A)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| AUTH-01 | POST | `/auth/signup` | 회원가입 (ID·비밀번호) | 0.1 | 공개 |
| AUTH-02 | GET | `/auth/login-id/check?loginId=` | ID 중복 확인 | 0.1 | 공개 |
| AUTH-03 | POST | `/auth/login` | 로그인 | 0.1 | 공개 |
| USER-01 | GET | `/users/me` | 내 정보(이름, 관계), 대상자별 역할, 받은 초대 수 | 1.1, 6.1 | 로그인 |
| USER-02 | PATCH | `/users/me` | 보호자 이름·관계 수정. 초대받아 가입한 사람의 0.2-1 저장에도 사용 | 0.2-1, 6.1 | 로그인 |
| USER-03 | DELETE | `/users/me` | 탈퇴 | 6.1 | 로그인 |
| TERMS-01 | GET | `/terms` | 동의 항목과 현재 버전 | 0.2-4 | 공개 |

### 2-2. 온보딩·대상자 (백엔드 B)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| ONB-01 | POST | `/onboarding` | 0.2-1~0.2-4 입력을 한 번에 등록: 보호자 프로필 + 대상자 + 돌봄 프로필(Q1~Q5) + 동의 → 초기 설명서 생성 | 0.2-4 | 로그인 |
| SUBJ-02 | GET | `/subjects/{subjectId}` | 대상자 기본 정보 | 1.1, 3.2 | OWNER, CO |
| SUBJ-03 | PATCH | `/subjects/{subjectId}` | 대상자 기본 정보·비상 연락처 수정 | 3.2 | OWNER |

- 0.2-1~0.2-3의 입력값은 클라이언트가 들고 있다가 0.2-4 "등록 완료"에서 ONB-01 한 번으로 보낸다. 중간 저장 API는 없다.
- 0.2-3 질문 답변은 AI를 거치지 않고 답변 하나가 설명서 항목 하나가 된다(Q1→DAILY_LIFE, Q2→MEDICATION, Q3·Q4→BEHAVIOR_RESPONSE, Q5→COMMUNICATION).

### 2-3. 홈 (백엔드 A)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| HOME-01 | GET | `/subjects/{subjectId}/home` | 홈 화면 데이터 | 1.1 | OWNER, CO |

### 2-4. 일기·AI 분석·검토 (백엔드 B)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| STT-01 | POST | `/stt` | 음성 → 텍스트. 3분·10MB 이내. 음성 미저장 | 2.1 | 로그인 |
| DIARY-01 | POST | `/subjects/{subjectId}/diaries` | 일기 저장 | 2.1 | OWNER |
| DIARY-02 | POST | `/subjects/{subjectId}/diaries/{diaryId}/analyze` | AI 분석 (동기, AI-01, 최대 25초) | 2.1→2.2 | OWNER |
| DIARY-03 | GET | `/subjects/{subjectId}/diaries?from=&to=&page=&size=` | 일기 목록 (전체·이번 주·지난달 필터는 from/to로) | 2.4 | OWNER |
| DIARY-04 | GET | `/subjects/{subjectId}/diaries/{diaryId}` | 일기 상세 (원문, 공감, 추출 항목) | 2.4, 2.3 | OWNER |
| DIARY-05 | DELETE | `/subjects/{subjectId}/diaries/{diaryId}` | 일기 삭제 | 2.4 | OWNER |
| ITEM-01 | POST | `/subjects/{subjectId}/diaries/{diaryId}/items/review` | 선택 항목 승인(수정 포함) → 설명서 반영 | 2.3 | OWNER |

- ITEM-01은 승인 항목을 설명서에 반영하는 API라 백엔드 A(guide 패키지)가 맡는다. 나머지는 백엔드 B.
- STT-01은 2.1 녹음에서 쓴다. 변환된 텍스트는 저장하지 않고 돌려주기만 한다. 보호자가 확인·수정한 뒤 DIARY-01로 저장한다.
- 2.4 화면은 역할에 따라 다른 API를 쓴다. OWNER는 DIARY-03·04, CO는 REPORT-01·02.
- DIARY-02 요청의 클라이언트 타임아웃은 30초 이상(서버 상한 25초).

### 2-5. 리포트 (백엔드 C)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| REPORT-01 | GET | `/subjects/{subjectId}/daily-records?from=&to=` | 승인 항목이 있는 날짜 목록 | 2.4 | OWNER, CO |
| REPORT-02 | GET | `/subjects/{subjectId}/daily-records/{diaryId}` | 일일 기록 (승인 항목만) | 2.4 | OWNER, CO |
| REPORT-03 | GET | `/subjects/{subjectId}/weekly-reports?endDate=` | 주간 리포트: endDate 포함 최근 7일 (기본 오늘) (P1, AI-05) | 1.1, 2.4→2.5 | OWNER, CO |

### 2-6. 설명서 (백엔드 A)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| GUIDE-01 | GET | `/subjects/{subjectId}/guide` | 설명서 전체 (기본 정보 + 7개 섹션) | 3.1 | OWNER, CO |
| GUIDE-02 | POST | `/subjects/{subjectId}/guide/entries` | 항목 추가 (MANUAL) | 3.2 | OWNER |
| GUIDE-03 | PATCH | `/subjects/{subjectId}/guide/entries/{entryId}` | 항목 수정 | 3.2 | OWNER |
| GUIDE-04 | DELETE | `/subjects/{subjectId}/guide/entries/{entryId}` | 항목 삭제 | 3.2 | OWNER |

- 3.2의 기본 정보(이름, 출생 연도, 지역, 비상 연락처) 수정은 SUBJ-03을 쓴다.
- 공동 관리자는 설명서 전체를 조회한다. 항목별 공개 범위는 없다.

### 2-7. 공유 (백엔드 A)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| SHARE-01 | GET | `/subjects/{subjectId}/shares/section-options?documentType=&sourceDiaryId=&periodStart=` | 문서 유형별 공개 가능 섹션 | 4.1 | OWNER, CO |
| SHARE-02 | POST | `/subjects/{subjectId}/shares/drafts` | 공유 초안 + 미리보기 | 4.1→4.2 | OWNER, CO |
| SHARE-03 | POST | `/subjects/{subjectId}/shares/{shareId}/approve` | 승인, 링크 발급 (이때 한 번만 제공) | 4.2→4.3 | 초안 생성자 |
| SHARE-04 | GET | `/subjects/{subjectId}/shares` | 공유 내역 | 4.3 | OWNER, CO |
| SHARE-05 | POST | `/subjects/{subjectId}/shares/{shareId}/revoke` | 링크 접근 중지 | 4.3 | OWNER 전체, CO 본인 생성분 |
| PUB-01 | GET | `/public/shares/{token}` | 수신자 열람 (고정본만) | 4.4 | 공개 |

- 유효기간은 승인 시각부터 7일 고정
- 공유 진입: 2.4(DAILY), 2.5(WEEKLY), 3.1(GUIDE), 5.3(GUIDE 초기 선택)
- 수신자 유형은 없다. 받는 사람 메모(recipientLabel)만 선택 입력한다.

### 2-8. 공동 관리자 (백엔드 A)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| MEMBER-01 | GET | `/users/lookup?loginId=` | 계정 존재 확인 (표시명만). 분당 10회 제한 | 6.2 | 로그인 |
| INVITE-01 | POST | `/subjects/{subjectId}/invitations` | 초대 발송 | 6.2 | OWNER |
| INVITE-02 | GET | `/subjects/{subjectId}/invitations?status=` | 보낸 초대 목록 | 6.1 | OWNER |
| INVITE-03 | POST | `/subjects/{subjectId}/invitations/{invitationId}/cancel` | 대기 중 초대 취소 | 6.1 | OWNER |
| INVITE-04 | GET | `/users/me/invitations` | 받은 초대 목록 | 6.2.1 | 로그인 |
| INVITE-05 | POST | `/users/me/invitations/{invitationId}/accept` | 수락 | 6.2.1 | 초대 대상자 |
| INVITE-06 | POST | `/users/me/invitations/{invitationId}/reject` | 거절 | 6.2.1 | 초대 대상자 |
| MEMBER-02 | GET | `/subjects/{subjectId}/members` | 공동 관리자 목록 (관계·계정 ID 포함) | 6.1, 6.3 | OWNER, CO |
| MEMBER-03 | DELETE | `/subjects/{subjectId}/members/{userId}` | 공동 관리자 삭제 + 그 사람이 만든 활성 링크 철회 | 6.3 | OWNER |

- 권한은 고정이다. 6.2의 권한 목록은 화면 표시용이며 요청에 권한 값을 보내지 않는다.

### 2-9. 돌봄 지원 (백엔드 C)

| ID | Method | Path | 설명 | 화면 | 권한 |
|---|---|---|---|---|---|
| RES-02 | GET | `/subjects/{subjectId}/resources/search?careTerm=&neededDate=&district=&dong=&kind=` | 조건으로 기관·제도 검색 (저장 없음) | 5.1→5.2, 5.0→5.2 | OWNER, CO |
| RES-01 | GET | `/resources/{resourceId}` | 기관·제도 상세 | 5.3 | 로그인 |
| SUP-01 | GET | `/support/burnout-guide` | 소진 지원 안내 문구 + 제도 목록 | 5.4 | 로그인 |
| SUP-02 | GET | `/support/crisis-guide` | 위기 안전 안내 문구 + 연락처 | 5.5 | 로그인 |

- 5.0 돌봄 지원 메뉴는 고정 화면이라 API가 없다.
- 5.0에서 5.2로 바로 들어오면 RES-02를 careTerm 없이 지역만으로 호출한다.

### 2-10. 관리자

| ID | Method | Path | 설명 | 권한 |
|---|---|---|---|---|
| ADMIN-01 | POST | `/admin/public-data/sync` | 공공데이터 적재·갱신 | X-Admin-Key |
| ADMIN-02 | PUT | `/admin/ai-mode` | AI_MODE 전환 (구현 여부 미정) | X-Admin-Key |

### 2-11. P1 (MVP 이후)

| ID | Method | Path | 설명 |
|---|---|---|---|
| GAP-01~05 | | `/subjects/{subjectId}/care-gaps/**` | AI가 일기에서 감지한 돌봄 공백 저장·확인·기각 (ERD 3-5 care_gap) |
| GAP-06 | GET | `/subjects/{subjectId}/care-gaps/{gapId}/recommendations` | 공백별 추천 저장·조회 (AI-04 추천 이유) |

---

## 3. 상세

### AUTH-01 회원가입
```json
// Request
{ "loginId": "mom1234", "password": "abcd1234" }
// Response 201
{ "userId": 1 }
```
- loginId: 4~20자, 영문 소문자·숫자 / password: 8~20자, 영문·숫자 포함
- 이름·관계는 0.2-1에서 받는다(ONB-01 또는 USER-02).

에러: `AUTH_INVALID_LOGIN_ID_FORMAT` 400, `AUTH_INVALID_PASSWORD_FORMAT` 400, `AUTH_DUPLICATE_LOGIN_ID` 409

### AUTH-03 로그인
```json
// Request
{ "loginId": "mom1234", "password": "abcd1234" }
// Response 200
{
  "accessToken": "eyJ...",
  "expiresIn": 604800,
  "user": { "userId": 1, "loginId": "mom1234", "displayName": "김영희", "relation": "PARENT" },
  "subjects": [ { "subjectId": 10, "name": "민지", "role": "OWNER" } ],
  "pendingInvitationCount": 0
}
```
로그인 후 화면 분기
- subjects가 있으면 1.1
- subjects가 비어 있고 받은 초대가 있으면 6.2.1 (대상자를 새로 등록하지 않음. 이름·관계가 비어 있으면 0.2-1만 거친 뒤 USER-02로 저장)
- 둘 다 없으면 0.2-1

에러: `AUTH_INVALID_CREDENTIALS` 401, `AUTH_TOO_MANY_ATTEMPTS` 429

### USER-01 내 정보
```json
{
  "userId": 1, "loginId": "mom1234", "displayName": "김영희", "relation": "PARENT",
  "subjects": [ { "subjectId": 10, "name": "민지", "role": "OWNER" } ],
  "pendingInvitationCount": 1
}
```

### USER-02 보호자 프로필 수정
```json
// Request
{ "displayName": "김영희", "relation": "PARENT" }
// Response 200
{ "userId": 1, "displayName": "김영희", "relation": "PARENT" }
```
relation: PARENT / OTHER

### TERMS-01 동의 항목
```json
{
  "terms": [
    { "consentType": "PRIVACY", "termsVersion": "1.0", "required": true, "title": "개인정보 수집·이용", "content": "..." },
    { "consentType": "SENSITIVE_INFO", "termsVersion": "1.0", "required": true, "title": "민감정보 처리", "content": "..." },
    { "consentType": "AI_PROCESSING", "termsVersion": "1.0", "required": true, "title": "AI 기반 정보 처리 안내", "content": "..." },
    { "consentType": "SUBJECT_AUTHORITY", "termsVersion": "1.0", "required": true, "title": "돌봄 대상자 정보 처리 권한 확인", "content": "..." }
  ]
}
```
0.2-4의 "민감정보·AI 동의" 체크박스 하나에 SENSITIVE_INFO와 AI_PROCESSING을 함께 묶어도 된다. 요청에는 두 항목 모두 보낸다.

### ONB-01 온보딩 일괄 등록
```json
// Request
{
  "guardian": { "displayName": "김영희", "relation": "PARENT" },
  "subject": { "name": "민지", "birthYear": 2001, "district": "SUJI", "dong": "풍덕천동" },
  "profile": {
    "Q1": "아침 7시에 일어나 8시에 주간활동센터에 가요.",
    "Q2": "매운 음식을 못 먹고, 저녁 약은 식후 30분에 먹어요.",
    "Q3": "큰 소리가 나면 귀를 막고 소리를 질러요.",
    "Q4": "차에 태워 트로트를 틀어주면 진정돼요.",
    "Q5": null
  },
  "consents": [
    { "consentType": "PRIVACY", "termsVersion": "1.0" },
    { "consentType": "SENSITIVE_INFO", "termsVersion": "1.0" },
    { "consentType": "AI_PROCESSING", "termsVersion": "1.0" },
    { "consentType": "SUBJECT_AUTHORITY", "termsVersion": "1.0" }
  ]
}
// Response 201
{ "subjectId": 10, "createdGuideEntryCount": 4 }
```
- 한 트랜잭션에서 users 이름·관계 갱신, care_subject 생성, consent 저장, guide_entry 생성(source_type ONBOARDING)을 처리한다.
- profile 답변은 AI를 거치지 않는다. null이거나 빈 답변은 항목을 만들지 않는다(공란 처리, PRD 0.2-3).
- 이미 소유한 대상자가 있으면 409 (MVP는 사용자당 대상자 1명).

에러: `SUBJECT_INVALID_NAME` 400, `SUBJECT_INVALID_BIRTH_YEAR` 400, `SUBJECT_CONSENT_REQUIRED` 400, `ONB_ALREADY_COMPLETED` 409

### SUBJ-02 대상자 정보
```json
{ "subjectId": 10, "name": "민지", "birthYear": 2001, "age": 25, "district": "SUJI", "dong": "풍덕천동", "emergencyContact": "어머니 010-0000-0000" }
```
`age`는 birthYear로 계산한 값이다.

### SUBJ-03 대상자 정보 수정
```json
// Request (바꿀 값만)
{ "emergencyContact": "어머니 010-0000-0000, 아버지 010-1111-1111" }
// Response 200
{ "subjectId": 10, "updatedAt": "2026-11-03T21:00:00+09:00" }
```

### HOME-01 홈
```json
{
  "role": "OWNER",
  "name": "민지",
  "todayRecorded": false,
  "recentActivities": [
    { "diaryId": 501, "diaryDate": "2026-11-03", "preview": "오후에 옆 건물 공사 소리가...", "approvedCount": 3, "pendingCount": 0 }
  ],
  "pendingInvitationCount": 0
}
```
- CO에게는 `preview`가 null이다(일기 원문 비공개).
- 최근 활동을 누르면 OWNER는 DIARY-04, CO는 REPORT-02를 호출한다.

### STT-01 음성 변환
- Request: `multipart/form-data`, 필드 `audio`
  - 받는 형식: `audio/webm`(Chrome·Android), `audio/mp4`·`audio/m4a`(Safari·iOS). 브라우저가 녹음한 형식 그대로 보낸다
  - 3분, 10MB 이내. 3분이 되면 프론트가 녹음을 멈춘다
- Response 200: `{ "text": "오후에 옆 건물 공사 소리가 나니까..." }`
- 서버 처리 20초 이내, 재시도 없음. `AI_MODE=mock`이면 고정 문장 반환

에러: `STT_FILE_TOO_LARGE` 413, `STT_UNSUPPORTED_FORMAT` 400, `STT_FAILED` 502

### DIARY-01 일기 저장
```json
// Request
{ "diaryDate": "2026-11-03", "content": "오후에 옆 건물 공사 소리가...", "inputType": "VOICE" }
// Response 201
{ "diaryId": 501, "analysisStatus": "PENDING" }
```
에러: `DIARY_EMPTY_CONTENT` 400

### DIARY-02 AI 분석
**Response 200: 일반**
```json
{
  "analysisNo": 1,
  "result": "REVIEW",
  "empathyText": "공사 소리 때문에 오후 내내 긴장하셨겠어요.",
  "itemCount": 3,
  "items": [
    {
      "itemNo": 1, "category": "TRIGGER", "section": "BEHAVIOR_RESPONSE", "label": null,
      "content": "옆 건물 공사 소리가 나자 불안해함",
      "timeSlot": "AFTERNOON", "durationMin": null, "episodeNo": 1,
      "evidenceQuote": "오후에 옆 건물 공사 소리가 나니까", "status": "PENDING"
    },
    {
      "itemNo": 2, "category": "BEHAVIOR", "section": "BEHAVIOR_RESPONSE", "label": "소리 지름",
      "content": "30분 넘게 소리를 지름",
      "timeSlot": "AFTERNOON", "durationMin": 30, "episodeNo": 1,
      "evidenceQuote": "30분 넘게 소리를 질렀다", "status": "PENDING"
    },
    {
      "itemNo": 3, "category": "RESPONSE", "section": "BEHAVIOR_RESPONSE", "label": null,
      "content": "차에 태워 트로트를 틀어주니 조용해짐",
      "timeSlot": "AFTERNOON", "durationMin": null, "episodeNo": 1,
      "evidenceQuote": "차에 태워서 트로트 틀어주니까 조용해졌다", "status": "PENDING"
    }
  ],
  "burnoutNotice": false
}
```

**Response 200: 위기**
```json
{ "analysisNo": 1, "result": "CRISIS", "empathyText": null, "itemCount": 0, "items": [], "burnoutNotice": false }
```

- `result`: REVIEW(검토할 항목 있음 → 2.3) / NO_ITEMS(추출 결과 없음 안내) / CRISIS(일기는 저장됨, 5.5로 바로 이동)
- `section`은 서버가 category를 섹션으로 매핑한 값이다.
- `burnoutNotice: true`면 소진 지원 안내 카드를 띄우고, 누르면 5.4로 간다. 카드 위치는 2.2 하단을 기본으로 한다.
- LLM 호출이 실패해도 위기 키워드가 적중하면 CRISIS로 응답한다.
- 돌봄 공백 후보 감지는 P1이다(MVP 응답에 없음).

에러: `AI_CONSENT_REQUIRED` 403, `DIARY_ALREADY_ANALYZING` 409, `AI_TIMEOUT` 502 (일기 보존, 재시도 가능)

### DIARY-03 일기 목록
```json
{
  "content": [
    { "diaryId": 501, "diaryDate": "2026-11-03", "preview": "오후에 옆 건물...", "analysisStatus": "DONE", "reviewStatus": "APPROVED", "pendingCount": 0, "approvedCount": 3 }
  ],
  "page": 0, "size": 20, "totalElements": 14
}
```
`reviewStatus`는 계산값이다: 분석 전 NONE, PENDING 항목이 있으면 PENDING, 없으면 APPROVED.

### DIARY-04 일기 상세
```json
{
  "diaryId": 501, "diaryDate": "2026-11-03", "content": "...", "inputType": "VOICE",
  "analysisStatus": "DONE", "reviewStatus": "APPROVED",
  "empathyText": "공사 소리 때문에 오후 내내 긴장하셨겠어요.",
  "items": [ { "itemNo": 1, "category": "TRIGGER", "section": "BEHAVIOR_RESPONSE", "content": "...", "evidenceQuote": "...", "status": "APPROVED", "edited": false } ]
}
```
위기 판정 일기는 `empathyText`가 null이고 `items`가 비어 있다. 위기 여부 자체는 응답에 넣지 않는다.

### ITEM-01 검토·반영
```json
// Request
{
  "decisions": [
    { "itemNo": 1, "status": "APPROVED" },
    { "itemNo": 2, "status": "APPROVED", "content": "20~30분간 소리를 지름" },
    { "itemNo": 3, "status": "EXCLUDED" }
  ]
}
// Response 200
{ "approvedCount": 2, "excludedCount": 1, "duplicateSkippedCount": 0, "createdGuideEntryIds": [301, 302] }
```
- 화면의 "선택"이 APPROVED, "선택 해제"가 EXCLUDED다.
- `content`가 있으면 수정 후 승인(edited=true).
- 같은 섹션에 같은 내용이 이미 있으면 설명서 항목을 새로 만들지 않고 `duplicateSkippedCount`에 센다(PRD 2.3 중복 저장 방지).

에러: `GUIDE_ITEM_NOT_FOUND` 404, `GUIDE_NOTHING_TO_APPLY` 400

### REPORT-01 일일 기록 날짜 목록
```json
{
  "from": "2026-10-28", "to": "2026-11-03",
  "records": [
    { "diaryId": 501, "diaryDate": "2026-11-03", "approvedCount": 3, "summary": "큰 소리에 불안 반응 · 음악으로 안정" }
  ]
}
```
- 승인(APPROVED) 항목이 하나 이상 있는 일기만 나온다.
- `summary`는 승인 항목의 `content`를 이은 한 줄이다. 일기 원문, 공감 문장, 근거 문장은 쓰지 않는다. 공동 관리자에게는 원문 preview를 주지 않는다(HOME-01과 같은 규칙).
- `from`·`to`를 생략했을 때의 기본 구간은 구현 PR에서 정해 여기에 적는다.

### REPORT-02 일일 기록
```json
{
  "diaryId": 501, "diaryDate": "2026-11-03",
  "sections": [
    { "section": "BEHAVIOR_RESPONSE", "items": [ { "content": "30분 넘게 소리를 지름", "timeSlot": "AFTERNOON", "durationMin": 30 } ] }
  ]
}
```
APPROVED 항목만. 원문, 공감 문장, 근거 문장은 넣지 않는다.

### REPORT-03 주간 리포트 (P1)
```json
{
  "periodStart": "2026-10-28", "periodEnd": "2026-11-03",
  "recordCount": 5,
  "behaviorCount": 7,
  "summaryLines": ["이번 주에는 소리를 지르는 행동이 3번 기록됐어요.", "주로 오후에 있었고, 차에서 트로트를 틀어준 대처가 2번 있었어요."],
  "dailyTrend": [
    { "date": "2026-10-28", "recordCount": 1, "behaviorCount": 2 },
    { "date": "2026-10-29", "recordCount": 0, "behaviorCount": 0 }
  ],
  "keyChanges": [
    { "diaryId": 503, "diaryDate": "2026-10-31", "section": "PREFERENCE", "content": "담요를 덮어주면 안정됨" }
  ],
  "repeatedBehaviors": [ { "label": "소리 지름", "count": 3 } ],
  "timePattern": [ { "timeSlot": "AFTERNOON", "count": 3 }, { "timeSlot": "EVENING", "count": 1 } ]
}
```
- 기간: endDate 포함 최근 7일. `dailyTrend`는 7일 모두 내려준다(그래프용).
- `recordCount`(일기 수)와 `behaviorCount`(승인된 BEHAVIOR 항목 수)를 구분한다(PRD 2.5).
- `keyChanges`: 이 기간에 설명서에 새로 반영된 항목. 누르면 해당 일기 상세로 간다. AI가 판단하지 않는다.
- 기록이 0건이면 `recordCount: 0`과 빈 배열(화면은 "생성 불가" 안내). 3건 미만이면 `summaryLines: []`.
- 원인을 설명하는 문장은 넣지 않는다.

### GUIDE-01 설명서
```json
{
  "basicInfo": { "name": "민지", "age": 25, "district": "SUJI", "dong": "풍덕천동", "emergencyContact": "어머니 010-0000-0000" },
  "updatedAt": "2026-11-03T21:10:00+09:00",
  "sections": [
    { "section": "PROHIBITED", "entries": [] },
    { "section": "HEALTH_RISK", "entries": [] },
    { "section": "COMMUNICATION", "entries": [] },
    { "section": "BEHAVIOR_RESPONSE", "entries": [ { "entryId": 301, "content": "공사 소음에 불안해함", "sourceType": "DIARY", "updatedAt": "..." } ] },
    { "section": "DAILY_LIFE", "entries": [] },
    { "section": "MEDICATION", "entries": [] },
    { "section": "PREFERENCE", "entries": [] }
  ],
  "editable": true
}
```
- 7개 섹션은 PRD 3.1 순서로, 항목이 없어도 항상 내려준다.
- `editable`: OWNER true, CO false

### GUIDE-02 항목 추가
```json
// Request
{ "section": "PROHIBITED", "content": "갑자기 뒤에서 어깨를 잡지 않기" }
// Response 201
{ "entryId": 310 }
```

### GUIDE-03 항목 수정
```json
// Request
{ "content": "갑자기 뒤에서 어깨를 잡거나 큰 소리로 부르지 않기" }
// Response 200
{ "entryId": 310, "updatedAt": "2026-11-03T22:00:00+09:00" }
```
- section: PROHIBITED, HEALTH_RISK, COMMUNICATION, BEHAVIOR_RESPONSE, DAILY_LIFE, MEDICATION, PREFERENCE
- content 1~500자. 섹션 변경은 삭제 후 추가

### SHARE-01 섹션 옵션
```json
{
  "documentType": "GUIDE",
  "options": [
    { "sectionCode": "BEHAVIOR_RESPONSE", "defaultOn": true, "hasContent": true },
    { "sectionCode": "HEALTH_RISK", "defaultOn": true, "hasContent": false }
  ],
  "emergencyContact": { "available": true, "defaultOn": false }
}
```
- GUIDE·DAILY: 설명서 7개 섹션 / WEEKLY: WEEKLY_SUMMARY, DAILY_TREND, KEY_CHANGES
- 내용이 있는 섹션은 모두 기본 ON이다. `hasContent: false`는 선택할 수 없다.
- 비상 연락처는 기본 OFF, 보호자가 켤 때만 포함한다.

### SHARE-02 초안 생성
```json
// Request
{
  "documentType": "GUIDE",
  "sourceDiaryId": null,
  "periodStart": null,
  "sectionCodes": ["BEHAVIOR_RESPONSE", "COMMUNICATION", "MEDICATION"],
  "showEmergencyContact": true,
  "recipientLabel": "목요일 선생님"
}
// Response 201
{
  "shareId": 900,
  "preview": {
    "basicInfo": { "name": "민지", "age": 25, "emergencyContact": "어머니 010-0000-0000" },
    "documentType": "GUIDE",
    "summaryLines": [],
    "sections": [ { "section": "BEHAVIOR_RESPONSE", "entries": ["공사 소음에 불안해함"] } ]
  },
  "validDays": 7
}
```
- 본문은 승인 항목 원문 그대로. 이 미리보기가 그대로 고정본이 된다.
- 고정본의 기본 정보는 이름·나이만, 비상 연락처는 `showEmergencyContact`가 true일 때만 넣는다. 지역·출생 연도는 넣지 않는다.
- WEEKLY는 `periodStart`(7일 시작일), DAILY는 `sourceDiaryId`가 필수다.
- `summaryLines`(AI-03 상단 요약)는 P1이다. MVP에서는 빈 배열이다.

에러: `SHARE_NO_SECTION` 400, `SHARE_SOURCE_REQUIRED` 400, `SHARE_NO_APPROVED_CONTENT` 400

### SHARE-03 승인
```json
// Response 200
{
  "shareId": 900,
  "shareUrl": "https://{도메인}/s/{token}",
  "documentType": "GUIDE",
  "approvedAt": "2026-11-03T21:30:00+09:00",
  "expiresAt": "2026-11-10T21:30:00+09:00",
  "notice": "이 링크는 지금만 복사할 수 있어요."
}
```
`shareUrl`은 이 응답에서만 제공한다.

에러: `SHARE_NOT_FOUND` 404 (초안 삭제됨), `SHARE_ALREADY_APPROVED` 409, `SHARE_FORBIDDEN` 403

### SHARE-04 공유 내역
```json
{
  "shares": [
    {
      "shareId": 900, "documentType": "GUIDE", "recipientLabel": "목요일 선생님",
      "createdByName": "김영희", "approvedAt": "...", "expiresAt": "...",
      "status": "ACTIVE", "revocable": true
    }
  ]
}
```
- `status`: ACTIVE / EXPIRED / REVOKED, 조회 시점에 계산. 초안은 목록에 없다. 링크는 다시 제공하지 않는다.

### SHARE-05 접근 중지
Response 200: `{ "shareId": 900, "status": "REVOKED" }`

에러: `SHARE_FORBIDDEN` 403, `SHARE_ALREADY_INACTIVE` 409

### PUB-01 수신자 열람
```json
{
  "documentType": "GUIDE",
  "sharedAt": "2026-11-03T21:30:00+09:00",
  "expiresAt": "2026-11-10T21:30:00+09:00",
  "content": {
    "basicInfo": { "name": "민지", "age": 25, "emergencyContact": "어머니 010-0000-0000" },
    "summaryLines": [],
    "sections": [ { "section": "BEHAVIOR_RESPONSE", "entries": ["..."] } ]
  }
}
```
- 별도 확인 절차 없이 링크로 바로 열람한다.
- 응답 헤더: `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Robots-Tag: noindex` / IP당 분당 30회 제한

에러: `SHARE_NOT_FOUND` 404, `SHARE_EXPIRED` 410, `SHARE_REVOKED` 410, `COMMON_TOO_MANY_REQUESTS` 429

### MEMBER-01 계정 확인
Response 200: `{ "userId": 2, "displayName": "이철수" }`

에러: `INVITE_USER_NOT_FOUND` 404, `COMMON_TOO_MANY_REQUESTS` 429

### INVITE-01 초대 발송
```json
// Request
{ "inviteeLoginId": "dad5678" }
// Response 201
{ "invitationId": 70, "status": "PENDING" }
```
에러: `INVITE_USER_NOT_FOUND` 404, `INVITE_SELF` 400, `INVITE_ALREADY_MEMBER` 409, `INVITE_DUPLICATE_PENDING` 409

### INVITE-02 보낸 초대 목록
```
GET /subjects/10/invitations?status=PENDING
```
```json
{
  "invitations": [
    {
      "invitationId": 70,
      "inviteeLoginId": "dad5678",
      "inviteeDisplayName": "이철수",
      "status": "PENDING",
      "createdAt": "2026-11-03T20:00:00+09:00",
      "respondedAt": null
    }
  ]
}
```
- `status`는 PENDING / ACCEPTED / REJECTED / CANCELED / REVOKED. 생략하면 전체이고 최신순이다.
- `respondedAt`은 PENDING이면 null이다.
- 취소(INVITE-03) 버튼은 `status`가 PENDING일 때만 보여준다.

에러: `COMMON_VALIDATION` 400 (알 수 없는 `status` 값)

### INVITE-04 받은 초대
```json
{
  "invitations": [
    {
      "invitationId": 70,
      "inviterDisplayName": "김영희",
      "subjectName": "민지",
      "permissions": ["돌봄 설명서 열람", "돌봄 정보 공유", "돌봄 지원 이용"],
      "restrictedItems": ["일기 원문", "설명서 수정", "보호자 상태 정보"],
      "createdAt": "..."
    }
  ]
}
```
- 수락 전에는 대상자의 표시 이름(`subjectName`)만 보여준다. 나이, 지역, 비상 연락처, 설명서 내용은 내려주지 않는다(PRD 6.2.1 "수락 이전 민감정보 접근 금지").
- `permissions`는 고정 목록이다.

### INVITE-05 수락
Response 200: `{ "subjectId": 10, "role": "CO_MANAGER" }`

에러: `INVITE_NOT_PENDING` 409, `INVITE_FORBIDDEN` 403

### MEMBER-02 공동 관리자 목록
```json
{
  "owner": { "userId": 1, "displayName": "김영희", "relation": "PARENT", "loginId": "mom1234" },
  "coManagers": [ { "userId": 2, "displayName": "이철수", "relation": "OTHER", "loginId": "dad5678", "activatedAt": "..." } ]
}
```
CO가 호출하면 loginId는 본인 것만 내려준다.

### MEMBER-03 공동 관리자 삭제
Response 200: `{ "revokedShareCount": 2 }`

- invitation → REVOKED, co_manager 행 삭제, 그 사람이 만든 활성 공유 철회를 한 트랜잭션에서 처리한다. 계정 자체는 삭제하지 않는다.
- 6.3 삭제 확인 팝업에 "이 분이 보낸 공유 링크도 함께 막힙니다" 안내 권장

에러: `MEMBER_NOT_FOUND` 404, `MEMBER_CANNOT_REMOVE_OWNER` 400

### RES-02 조건 검색
```
GET /subjects/10/resources/search?careTerm=SHORT&neededDate=2026-11-10&district=SUJI&kind=
```
```json
{
  "condition": { "careTerm": "SHORT", "neededDate": "2026-11-10", "district": "SUJI", "dong": null },
  "disclaimer": "실제 신청 자격과 이용 가능 여부는 해당 기관에 확인이 필요합니다.",
  "results": [
    { "resourceId": 1001, "kind": "PROGRAM", "name": "발달장애인 긴급돌봄", "summary": "보호자 입원 등으로 일시적 돌봄이 필요할 때 이용하는 제도", "reasonText": "단기 돌봄과 관련된 제도예요.", "officialUrl": "https://..." },
    { "resourceId": 2034, "kind": "FACILITY", "name": "○○ 단기거주시설", "district": "SUJI", "phone": "031-000-0000", "reasonText": "수지구에 있는 단기거주시설이에요.", "officialUrl": "https://..." }
  ]
}
```
- careTerm: SHORT(5.1-A 단기) / LONG(5.1-B 장기) / 생략(5.0에서 바로 진입, 지역만)
- district를 생략하면 대상자의 등록 지역을 쓴다.
- 제도는 care_term과 target_conditions(나이 등)로, 시설은 제도에 매핑된 시설 유형 + 같은 구 우선으로 고른다. 저장하지 않는다.
- `reasonText`는 MVP에서 템플릿 문장이다(AI-04는 P1).
- 시설이 0건이면 `results`에 제도(PROGRAM)만 담는다. 제도도 없으면 빈 배열(에러 아님).

에러: `RESOURCE_INVALID_CONDITION` 400

### RES-01 상세
```json
// 시설
{
  "resourceId": 2034, "kind": "FACILITY", "name": "○○ 단기거주시설",
  "phone": "031-000-0000", "officialUrl": "https://...",
  "facilityType": "단기거주시설", "district": "SUJI", "address": "...",
  "lat": 37.32, "lng": 127.09, "services": "...", "source": "YONGIN_API", "fetchedAt": "..."
}
// 제도
{
  "resourceId": 1001, "kind": "PROGRAM", "name": "발달장애인 긴급돌봄",
  "phone": "...", "officialUrl": "https://...",
  "programType": "EMERGENCY_CARE", "careTerm": "SHORT", "summary": "...", "eligibilitySummary": "...",
  "procedure": "...", "sourceDoc": "...", "docVersion": "2026"
}
```
5.3에서 "돌봄 설명서 공유"를 누르면 SHARE-01을 `documentType=GUIDE`로 호출한다. 기관에 개인정보를 자동 전송하지 않는다.

### SUP-01 소진 지원 안내
```json
{
  "message": ["요즘 기록에 지치신 날이 자주 보여요.", "보호자가 쉴 수 있도록 돕는 제도가 있어요."],
  "programs": [
    { "resourceId": 1101, "name": "발달장애인 부모상담 지원", "programType": "PARENT_COUNSELING" },
    { "resourceId": 1102, "name": "가족휴식지원", "programType": "FAMILY_REST" },
    { "resourceId": 1001, "name": "발달장애인 긴급돌봄", "programType": "EMERGENCY_CARE" }
  ]
}
```
5.0에서 직접 들어온 경우에도 같은 응답을 쓴다. 화면 문구만 다르게 할 수 있다(AI 명세 5-2).

### SUP-02 위기 안전 안내
```json
{
  "message": ["지금 많이 힘드신 것 같아요.", "혼자 감당하지 않으셔도 됩니다. 지금 바로 이야기할 수 있는 곳이 있어요."],
  "contacts": [
    { "name": "자살예방 상담전화", "phone": "109", "hours": "24시간" },
    { "name": "정신건강 위기상담", "phone": "1577-0199", "hours": "24시간" },
    { "name": "119", "phone": "119", "note": "지금 누군가 다쳤거나 위험할 때" },
    { "name": "112", "phone": "112", "note": "지금 누군가 다쳤거나 위험할 때" }
  ],
  "followUp": { "resourceId": 1101, "name": "발달장애인 부모상담 지원" }
}
```
문구는 AI 명세 5-1과 같아야 한다. "전문기관 지원 확인"은 followUp으로 5.3에 간다.

### ADMIN-01 공공데이터 적재·갱신
`POST /admin/public-data/sync`, 헤더 `X-Admin-Key`
```json
{ "source": "YONGIN_API", "upsertedCount": 252 }
```
- `upsertedCount`는 이번 실행에서 추가하거나 갱신한 시설 수다. 위 숫자는 예시이고 실제 값이 아니다.
- 키가 없거나 틀리거나 서버 키가 비어 있으면 `COMMON_FORBIDDEN` 403이다.

---

## 4. 에러 코드

| 코드 | HTTP | 메시지 |
|---|---|---|
| COMMON_VALIDATION | 400 | 입력한 내용을 다시 확인해 주세요. |
| COMMON_UNAUTHORIZED | 401 | 다시 로그인해 주세요. |
| COMMON_FORBIDDEN | 403 | 이용할 수 있는 권한이 없어요. |
| COMMON_NOT_FOUND | 404 | 정보를 찾을 수 없어요. |
| COMMON_TOO_MANY_REQUESTS | 429 | 잠시 후 다시 시도해 주세요. |
| COMMON_INTERNAL_ERROR | 500 | 잠시 후 다시 시도해 주세요. |
| AUTH_INVALID_LOGIN_ID_FORMAT | 400 | 아이디는 영문 소문자와 숫자로 4~20자예요. |
| AUTH_INVALID_PASSWORD_FORMAT | 400 | 비밀번호는 영문과 숫자를 넣어 8~20자로 만들어 주세요. |
| AUTH_DUPLICATE_LOGIN_ID | 409 | 이미 사용 중인 아이디예요. |
| AUTH_INVALID_CREDENTIALS | 401 | 아이디 또는 비밀번호가 맞지 않아요. |
| AUTH_TOO_MANY_ATTEMPTS | 429 | 잠시 후 다시 로그인해 주세요. |
| SUBJECT_INVALID_NAME | 400 | 이름은 2~20자로 입력해 주세요. |
| SUBJECT_INVALID_BIRTH_YEAR | 400 | 태어난 연도를 다시 확인해 주세요. |
| SUBJECT_CONSENT_REQUIRED | 400 | 필수 동의 항목에 동의해 주세요. |
| ONB_ALREADY_COMPLETED | 409 | 이미 등록을 마쳤어요. |
| STT_FILE_TOO_LARGE | 413 | 녹음이 너무 길어요. 3분 이내로 나눠서 녹음해 주세요. |
| STT_UNSUPPORTED_FORMAT | 400 | 녹음 파일을 읽을 수 없어요. |
| STT_FAILED | 502 | 음성을 글로 바꾸지 못했어요. 다시 시도하거나 글로 입력해 주세요. |
| DIARY_EMPTY_CONTENT | 400 | 내용을 입력해 주세요. |
| DIARY_ALREADY_ANALYZING | 409 | 분석 중이에요. 잠시만 기다려 주세요. |
| AI_CONSENT_REQUIRED | 403 | AI 분석 이용에 동의가 필요해요. |
| AI_TIMEOUT | 502 | 분석이 늦어지고 있어요. 기록은 저장됐으니 다시 시도해 주세요. |
| GUIDE_ITEM_NOT_FOUND | 404 | 항목을 찾을 수 없어요. |
| GUIDE_NOTHING_TO_APPLY | 400 | 반영할 항목을 하나 이상 선택해 주세요. |
| SHARE_NO_SECTION | 400 | 공개할 항목을 하나 이상 선택해 주세요. |
| SHARE_SOURCE_REQUIRED | 400 | 공유할 기록을 선택해 주세요. |
| SHARE_NO_APPROVED_CONTENT | 400 | 공유할 수 있는 승인된 정보가 없어요. |
| SHARE_ALREADY_APPROVED | 409 | 이미 발급된 공유예요. |
| SHARE_ALREADY_INACTIVE | 409 | 이미 만료되었거나 접근이 중지된 링크예요. |
| SHARE_FORBIDDEN | 403 | 이 공유를 관리할 권한이 없어요. |
| SHARE_NOT_FOUND | 404 | 링크를 찾을 수 없어요. |
| SHARE_EXPIRED | 410 | 유효기간이 지난 링크예요. 보호자에게 새 링크를 요청해 주세요. |
| SHARE_REVOKED | 410 | 더 이상 볼 수 없는 링크예요. |
| MEMBER_NOT_FOUND | 404 | 공동 관리자를 찾을 수 없어요. |
| MEMBER_CANNOT_REMOVE_OWNER | 400 | 주 보호자는 삭제할 수 없어요. |
| INVITE_USER_NOT_FOUND | 404 | 해당 아이디의 사용자가 없어요. |
| INVITE_SELF | 400 | 본인은 초대할 수 없어요. |
| INVITE_ALREADY_MEMBER | 409 | 이미 공동 관리자예요. |
| INVITE_DUPLICATE_PENDING | 409 | 이미 초대를 보냈어요. 수락을 기다려 주세요. |
| INVITE_NOT_PENDING | 409 | 이미 처리되었거나 취소된 초대예요. |
| INVITE_FORBIDDEN | 403 | 본인에게 온 초대가 아니에요. |
| RESOURCE_INVALID_CONDITION | 400 | 찾을 조건을 다시 확인해 주세요. |

---

## 5. 미결 사항

| # | 항목 | 영향 |
|---|---|---|
| 1 | ADMIN-02 구현 여부 | 시연 안정성 |
| 2 | 동의 약관 본문 | TERMS-01 (기획) |
| 3 | 소진 안내 카드 위치 (기본: 2.2 하단) | DIARY-02 burnoutNotice (기획) |
