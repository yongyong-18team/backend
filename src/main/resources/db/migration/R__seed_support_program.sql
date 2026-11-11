-- R__seed_support_program.sql
-- 지원제도 5개 규칙표 + 제도-시설 유형 매핑 시드 (#31)
--
-- 근거
--   - 규칙표: PR #82 docs/지원 제도 규칙표, 추천 기준 정리.md (2-1, 2-3, 3-2, 4, 5-3)
--   - #83 기획 답변: 가족휴식지원 -> 장애인복지관 매핑 제외, doc_version 불확실하면 비움
--   - #84: target_conditions 키는 minAge/maxAge (maxAge는 "미만"), 활동지원 65세 이상은 결과에 포함
--
-- 멱등: 반복 실행해도 같은 결과가 되게 이름(PROGRAM)과 program_type으로 찾아 갱신한다.
-- 시설(facility)은 #29·#30 적재 대상이라 여기서 넣지 않는다.
-- phone은 규칙표에 없어서 NULL이다. 지어내지 않는다.

DROP TABLE IF EXISTS seed_program;

CREATE TEMP TABLE seed_program (
    program_type        VARCHAR(30)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    care_term           VARCHAR(10)  NOT NULL,
    summary             TEXT         NOT NULL,
    eligibility_summary TEXT         NOT NULL,
    target_conditions   JSONB        NOT NULL,
    procedure           TEXT         NOT NULL,
    official_url        VARCHAR(300),
    source_doc          VARCHAR(200),
    doc_version         VARCHAR(20)
);

INSERT INTO seed_program VALUES
                             ('EMERGENCY_CARE', '발달장애인 긴급돌봄', 'SHORT',
                              '보호자에게 갑작스러운 돌봄 공백이 발생했을 때 일시적인 돌봄을 지원하는 제도',
                              '6세 이상~65세 미만 등록 발달장애인 중 보호자의 치료·입원, 경조사, 심리적 소진 등으로 일시돌봄이 필요한 경우',
                              '{"minAge": 6, "maxAge": 65}'::jsonb,
                              '지역발달장애인지원센터 신청 → 이용 대상 및 사유 확인 → 긴급돌봄 연계',
                              'https://www.jbaha.or.kr/main/filedown.php?menu=175&no=1539',
                              '2026년 발달장애인 긴급돌봄서비스 사업안내', '2026'),
                             ('DAY_ACTIVITY', '발달장애인 주간활동', 'LONG',
                              '성인 발달장애인의 낮 시간 활동과 지역사회 참여를 지원하는 제도',
                              '18세 이상 등록 지적·자폐성 장애인 대상. 취업·다른 서비스 이용 및 시설 입소 여부 등에 따라 일부 제한 또는 예외 존재',
                              '{"minAge": 18}'::jsonb,
                              '주소지 행정복지센터 신청 → 자격 결정 → 지정 제공기관 이용',
                              'https://www.donggu.go.kr/dg/download/viewer/1782364035991/index.html',
                              '2026년 발달장애인 활동서비스 사업안내', '2026'),
                             -- 활동지원: 규칙표는 6세 이상~65세 미만이지만 #84 결정에 따라 65세 이상도 결과에 넣는다.
                             -- 그래서 maxAge를 두지 않고, 연령 예외는 eligibility_summary로 안내한다.
                             ('ACTIVITY_SUPPORT', '장애인 활동지원', 'LONG',
                              '장애인의 일상생활과 사회활동에 필요한 도움을 지원하는 제도',
                              '기본 6세 이상~65세 미만 등록 장애인 대상. 방문조사와 자격심의를 거쳐 지원 여부를 결정하며 일부 연령 예외 존재',
                              '{"minAge": 6}'::jsonb,
                              '주소지 행정복지센터 등 신청 → 방문조사 → 수급자격 심의 → 활동지원기관 이용',
                              'https://www.ableservice.or.kr/regm/info/getOHGC0007M0.do',
                              '장애인활동지원 공식 제도소개', NULL),
                             ('FAMILY_REST', '발달장애인 가족휴식지원', 'SHORT',
                              '발달장애인 가족의 휴식과 여가활동을 지원하는 제도',
                              '발달장애인 및 가족 대상. 지역별 사업수행기관의 참여자 모집과 대상자 선정 절차에 따라 참여 가능',
                              '{}'::jsonb,
                              '지역 사업수행기관 모집 확인 → 신청서 및 증빙서류 제출 → 대상자 선정 → 프로그램 참여',
                              'https://www.koddi.or.kr/intro/openPriorInfo_view.jsp?brdNum=7413227&brdType=INFO08&readCnt=1',
                              '2026년 발달장애인지원센터 사업안내', '2026'),
                             ('PARENT_COUNSELING', '발달장애인 부모상담지원', 'LONG',
                              '발달장애인 부모와 보호자의 심리·정서적 부담 완화를 위한 상담지원 제도',
                              '등록 발달장애인 자녀의 부모·보호자 대상. 9세 미만 미등록 자녀에 대한 증빙서류 예외 존재',
                              '{}'::jsonb,
                              '주소지 행정복지센터 또는 복지로 신청 → 자격 확인 → 상담기관 이용',
                              'https://www.mohw.go.kr/menu.es?mid=a10710040500',
                              '보건복지부 발달장애인 부모상담지원', NULL);

-- 1) resource (PROGRAM): 이름으로 찾아 없으면 넣고, 있으면 링크만 갱신
INSERT INTO resource (resource_kind, name, phone, official_url)
SELECT 'PROGRAM', s.name, NULL, s.official_url
FROM seed_program s
WHERE NOT EXISTS (
    SELECT 1 FROM resource r
    WHERE r.resource_kind = 'PROGRAM' AND r.name = s.name
);

UPDATE resource r
SET official_url = s.official_url
    FROM seed_program s
WHERE r.resource_kind = 'PROGRAM' AND r.name = s.name;

-- 2) support_program: resource_id 기준 upsert
INSERT INTO support_program (resource_id, program_type, care_term, summary, eligibility_summary,
                             target_conditions, procedure, source_doc, doc_version)
SELECT r.resource_id, s.program_type, s.care_term, s.summary, s.eligibility_summary,
       s.target_conditions, s.procedure, s.source_doc, s.doc_version
FROM seed_program s
         JOIN resource r ON r.resource_kind = 'PROGRAM' AND r.name = s.name
    ON CONFLICT (resource_id) DO UPDATE SET
    program_type        = EXCLUDED.program_type,
                                     care_term           = EXCLUDED.care_term,
                                     summary             = EXCLUDED.summary,
                                     eligibility_summary = EXCLUDED.eligibility_summary,
                                     target_conditions   = EXCLUDED.target_conditions,
                                     procedure           = EXCLUDED.procedure,
                                     source_doc          = EXCLUDED.source_doc,
                                     doc_version         = EXCLUDED.doc_version;

-- 3) program_facility_type: 이 시드가 다루는 제도의 매핑은 지우고 다시 넣는다
--    긴급돌봄: 매핑 없음 (단기거주시설 0건)
--    가족휴식지원: 매핑 없음 (#83: 장애인복지관 3곳은 단기 이용 여부 미확인이라 제외)
DELETE FROM program_facility_type
WHERE program_id IN (
    SELECT r.resource_id
    FROM seed_program s
             JOIN resource r ON r.resource_kind = 'PROGRAM' AND r.name = s.name
);

INSERT INTO program_facility_type (program_id, facility_type)
SELECT r.resource_id, m.facility_type
FROM (VALUES
          ('DAY_ACTIVITY',      '주간이용센터'),
          ('ACTIVITY_SUPPORT',  '자립생활지원센터(IL)'),
          ('ACTIVITY_SUPPORT',  '장애인복지관'),
          ('PARENT_COUNSELING', '장애인복지관'),
          ('PARENT_COUNSELING', '장애아재활치료시설')
     ) AS m(program_type, facility_type)
         JOIN seed_program s ON s.program_type = m.program_type
         JOIN resource r ON r.resource_kind = 'PROGRAM' AND r.name = s.name;

DROP TABLE seed_program;
