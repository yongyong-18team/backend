package com.batong.analysis.service;

import com.batong.analysis.domain.AiAnalysis;
import com.batong.analysis.domain.ExtractedItem;
import com.batong.analysis.prompt.AnalyzeDiaryPrompt;
import com.batong.analysis.prompt.PromptLoader;
import com.batong.analysis.repository.AiAnalysisRepository;
import com.batong.analysis.repository.ExtractedItemRepository;
import com.batong.analysis.service.AnalysisResponseParser.ParsedAnalysis;
import com.batong.analysis.service.AnalysisResponseParser.ParsedItem;
import com.batong.diary.domain.AnalysisResult;
import com.batong.diary.domain.Diary;
import com.batong.diary.dto.DiaryDtos.AnalyzeResponse;
import com.batong.diary.dto.DiaryDtos.AnalyzedItem;
import com.batong.diary.repository.DiaryRepository;
import com.batong.global.auth.SubjectRole;
import com.batong.global.auth.SubjectRoleResolver;
import com.batong.global.enums.AnalysisStatus;
import com.batong.global.enums.ConsentType;
import com.batong.global.enums.ItemStatus;
import com.batong.global.enums.SafetyLevel;
import com.batong.global.enums.SignalType;
import com.batong.global.error.BusinessException;
import com.batong.global.error.ErrorCode;
import com.batong.infra.llm.LlmClient;
import com.batong.infra.llm.LlmException;
import com.batong.infra.llm.LlmRequest;
import com.batong.infra.llm.LlmResponse;
import com.batong.wellbeing.service.WellbeingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * DIARY-02 AI 분석 (TRD 8-3, AI 명세 3-4). LLM 호출은 트랜잭션 밖에서 한다: Tx1(상태 PROCESSING) → LLM → Tx2(저장).
 *
 * <p>위기 판정은 서버 키워드 OR LLM safetyLevel. 위기면 공감 문장·추출 항목을 저장하지 않는다.
 * LLM 요청·응답·일기 본문은 로그에 남기지 않는다(AI 명세 2-6).
 */
@Service
public class DiaryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DiaryAnalysisService.class);

    /** AI 명세 2-1 전체 상한 */
    static final Duration TOTAL_LIMIT = Duration.ofSeconds(25);
    private static final int EVIDENCE_COLUMN_MAX = 500;

    private final SubjectRoleResolver roleResolver;
    private final DiaryRepository diaryRepository;
    private final AiAnalysisRepository analysisRepository;
    private final ExtractedItemRepository itemRepository;
    private final WellbeingService wellbeingService;
    private final CrisisKeywordChecker keywordChecker;
    private final AnalysisResponseParser parser;
    private final PromptLoader promptLoader;
    private final LlmClient llmClient;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final String promptVersion;

    public DiaryAnalysisService(SubjectRoleResolver roleResolver, DiaryRepository diaryRepository,
                                AiAnalysisRepository analysisRepository, ExtractedItemRepository itemRepository,
                                WellbeingService wellbeingService, CrisisKeywordChecker keywordChecker,
                                AnalysisResponseParser parser, PromptLoader promptLoader, LlmClient llmClient,
                                JdbcClient jdbc, TransactionTemplate tx,
                                @Value("${ai.prompt.analyze-diary}") String promptVersion) {
        this.roleResolver = roleResolver;
        this.diaryRepository = diaryRepository;
        this.analysisRepository = analysisRepository;
        this.itemRepository = itemRepository;
        this.wellbeingService = wellbeingService;
        this.keywordChecker = keywordChecker;
        this.parser = parser;
        this.promptLoader = promptLoader;
        this.llmClient = llmClient;
        this.jdbc = jdbc;
        this.tx = tx;
        this.promptVersion = promptVersion;
    }

    public AnalyzeResponse analyze(Long subjectId, Long diaryId, Long userId) {
        roleResolver.require(subjectId, userId, SubjectRole.OWNER);
        Source source = tx.execute(status -> start(subjectId, diaryId, userId));

        try {
            boolean keywordHit = keywordChecker.matches(source.content());
            String masked = DiaryMasker.mask(source.content(), source.subjectName());
            LlmOutcome outcome = callLlm(source, masked);
            AnalyzeResponse response = tx.execute(status -> save(source, userId, keywordHit, outcome));
            if (response == null) {
                // LLM 실패 + 키워드 미적중: 일기는 FAILED로 남겼다(AI 명세 7장)
                throw new BusinessException(ErrorCode.AI_TIMEOUT);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            // 예상하지 못한 오류로 PROCESSING에 묶이지 않게 한다. 메시지에 본문이 섞일 수 있어 종류만 남긴다
            log.error("AI-01 analysis failed unexpectedly diaryId={} error={}", diaryId, e.getClass().getName());
            tx.executeWithoutResult(status -> diaryRepository.findById(diaryId).ifPresent(Diary::failAnalysis));
            throw e;
        }
    }

    /** Tx1: 동의 확인, 조건부 UPDATE로 PROCESSING (진행 중이면 409) */
    private Source start(Long subjectId, Long diaryId, Long userId) {
        boolean consented = jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM consent
                                        WHERE user_id = :u AND subject_id = :s AND consent_type = :t)""")
                .param("u", userId).param("s", subjectId).param("t", ConsentType.AI_PROCESSING.name())
                .query(Boolean.class).single();
        if (!consented) {
            throw new BusinessException(ErrorCode.AI_CONSENT_REQUIRED);
        }
        int updated = diaryRepository.startAnalysis(diaryId, subjectId, AnalysisStatus.PROCESSING,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        if (updated == 0) {
            boolean exists = diaryRepository.findByIdAndSubjectId(diaryId, subjectId).isPresent();
            throw new BusinessException(exists ? ErrorCode.DIARY_ALREADY_ANALYZING : ErrorCode.COMMON_NOT_FOUND);
        }
        Diary diary = diaryRepository.findById(diaryId).orElseThrow();
        String subjectName = jdbc.sql("SELECT name FROM care_subject WHERE subject_id = :s")
                .param("s", subjectId).query(String.class).single();
        return new Source(diaryId, diary.getDiaryDate(), diary.getContent(), subjectName);
    }

    /**
     * 트랜잭션 밖. 5xx·파싱 실패일 때만 1회 재시도하고, 타임아웃·4xx·네트워크 오류는 재시도하지 않는다(AI 명세 2-1).
     * 재시도해도 전체 상한(25초) 안에 끝날 수 있을 때만 다시 부른다.
     */
    private LlmOutcome callLlm(Source source, String masked) {
        LlmRequest request = AnalyzeDiaryPrompt.toRequest(
                promptLoader.load(AnalyzeDiaryPrompt.NAME, promptVersion), source.diaryDate(), masked, source.content());
        long startedAt = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            boolean retryable;
            try {
                LlmResponse response = llmClient.complete(request);
                ParsedAnalysis parsed = parser.parse(response.content(), masked);
                log.info("AI-01 ok diaryId={} model={} promptVersion={} attempt={} latencyMs={} droppedItems={}",
                        source.diaryId(), response.modelName(), promptVersion, attempt, response.latencyMs(),
                        parsed.droppedItemCount());
                return LlmOutcome.success(response, parsed);
            } catch (LlmException e) {
                log.warn("AI-01 llm failure diaryId={} promptVersion={} attempt={} type={}",
                        source.diaryId(), promptVersion, attempt, e.getType());
                retryable = e.isRetryable();
            } catch (AnalysisParseException e) {
                log.warn("AI-01 parse failure diaryId={} promptVersion={} attempt={}",
                        source.diaryId(), promptVersion, attempt);
                retryable = true;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            if (!retryable || attempt >= 2 || elapsed.plus(request.timeout()).compareTo(TOTAL_LIMIT) > 0) {
                return LlmOutcome.failure();
            }
        }
    }

    /** Tx2. LLM 실패 + 키워드 미적중이면 FAILED로 두고 null */
    private AnalyzeResponse save(Source source, Long userId, boolean keywordHit, LlmOutcome outcome) {
        Diary diary = diaryRepository.findById(source.diaryId()).orElseThrow();
        boolean crisis = keywordHit || (outcome.ok() && outcome.parsed().safetyLevel() == SafetyLevel.CRISIS);
        if (!crisis && !outcome.ok()) {
            diary.failAnalysis();
            return null;
        }

        int analysisNo = analysisRepository.findTopByDiaryIdOrderByAnalysisNoDesc(source.diaryId())
                .map(a -> a.getAnalysisNo() + 1).orElse(1);
        // 새 item_no는 기존 최댓값 다음부터(TRD 8-5). 지울 PENDING 번호를 다시 쓰지 않도록 지우기 전에 센다
        int nextItemNo = itemRepository.findTopByDiaryIdOrderByItemNoDesc(source.diaryId())
                .map(i -> i.getItemNo() + 1).orElse(1);
        itemRepository.deleteByDiaryIdAndStatus(source.diaryId(), ItemStatus.PENDING);

        if (crisis) {
            // LLM 실패 + 키워드 적중이면 raw_json·model_name은 NULL, prompt_version은 keyword-only (AI 명세 3-4-4)
            analysisRepository.save(outcome.ok()
                    ? AiAnalysis.crisis(source.diaryId(), analysisNo, outcome.response().content(), promptVersion,
                    outcome.response().modelName())
                    : AiAnalysis.crisis(source.diaryId(), analysisNo, null, AiAnalysis.KEYWORD_ONLY, null));
            wellbeingService.record(userId, source.diaryId(), SignalType.CRISIS);
            diary.completeAnalysis();
            return new AnalyzeResponse(analysisNo, AnalysisResult.CRISIS, null, 0, List.of(), false);
        }

        ParsedAnalysis parsed = outcome.parsed();
        String name = source.subjectName();
        List<AnalyzedItem> created = new ArrayList<>();
        for (ParsedItem p : parsed.items()) {
            String evidence = DiaryMasker.unmask(p.evidenceQuote(), name);
            if (evidence.codePointCount(0, evidence.length()) > EVIDENCE_COLUMN_MAX) {
                continue;
            }
            ExtractedItem item = itemRepository.save(ExtractedItem.pending(source.diaryId(), nextItemNo++,
                    p.category(), p.label(), DiaryMasker.unmask(p.content(), name), p.timeSlot(), p.durationMin(),
                    p.episodeNo(), evidence));
            created.add(new AnalyzedItem(item.getItemNo(), item.getCategory(), item.getCategory().getSection(),
                    item.getLabel(), item.getContent(), item.getTimeSlot(), p.durationMin(), p.episodeNo(),
                    item.getEvidenceQuote(), item.getStatus()));
        }
        String empathy = parsed.empathy() == null ? null : DiaryMasker.unmask(parsed.empathy(), name);
        analysisRepository.save(AiAnalysis.normal(source.diaryId(), analysisNo, empathy,
                outcome.response().content(), promptVersion, outcome.response().modelName()));
        // TRD 8-3: 소진 신호는 NORMAL일 때만 저장한다
        boolean burnoutNotice = parsed.burnoutSignal()
                && wellbeingService.record(userId, source.diaryId(), SignalType.BURNOUT);
        diary.completeAnalysis();
        AnalysisResult result = created.isEmpty() ? AnalysisResult.NO_ITEMS : AnalysisResult.REVIEW;
        return new AnalyzeResponse(analysisNo, result, empathy, created.size(), created, burnoutNotice);
    }

    private record Source(Long diaryId, LocalDate diaryDate, String content, String subjectName) {
    }

    private record LlmOutcome(boolean ok, LlmResponse response, ParsedAnalysis parsed) {
        static LlmOutcome success(LlmResponse response, ParsedAnalysis parsed) {
            return new LlmOutcome(true, response, parsed);
        }

        static LlmOutcome failure() {
            return new LlmOutcome(false, null, null);
        }
    }
}
