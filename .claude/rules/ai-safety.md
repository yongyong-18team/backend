---
paths:
  - "src/main/java/com/batong/analysis/**"
  - "src/main/java/com/batong/infra/llm/**"
  - "src/main/resources/prompts/**"
---
# AI 처리 규칙 (상세: docs/AI_명세_v1.4.md, docs/TRD_v1.4.md 8장)
- LLM 호출은 트랜잭션 밖에서 한다. 상태 변경 Tx → 호출 → 저장 Tx.
- 위기 판정은 서버 키워드 OR LLM safety_level. 하나라도 CRISIS면 CRISIS. 이때 공감 문장·추출 항목은 저장하지 않는다.
- LLM 실패 + 키워드 적중이면 raw_json·model_name은 NULL, prompt_version은 `keyword-only`.
- LLM 전송 전 대상자 이름을 "대상자"로 바꾸고, 전화번호를 지운다. 비상 연락처는 보내지 않는다.
- evidence_quote가 마스킹된 원문에 없으면 그 항목은 버린다(공백·문장부호 정규화 후 비교).
- 타임아웃 15초. 재시도는 5xx·파싱 실패일 때만 1회. 전체 상한 25초.
- 프롬프트 파일은 고치지 말고 새 버전 번호로 추가한다.
