---
paths:
  - "src/main/resources/db/migration/**"
---
- 파일 이름: `V{yyyyMMddHHmm}__{담당}_{설명}.sql` (예: `V202611051430__B_add_diary_index.sql`). 첫 파일만 `V1__init.sql`
- 이미 머지된 마이그레이션은 수정하지 않는다. 바꿀 게 있으면 새 파일을 만든다.
- 스키마를 바꾸면 `docs/ERD_v1.4.md` 수정이 필요하다고 PR 설명에 적는다.
