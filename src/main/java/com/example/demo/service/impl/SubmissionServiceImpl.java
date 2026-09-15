package com.example.demo.service.impl;

import com.example.demo.dto.response.AudioPlayResponse;
import com.example.demo.service.PaperShuffler;
import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.RoomPhase;
import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.Room;

import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamQuestionAnswer;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.SubmissionDetail;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.SaveAnswerRequest;
import com.example.demo.dto.request.SaveAnswersBatchRequest;
import com.example.demo.dto.request.SubmitExamRequest;
import com.example.demo.dto.response.AnswerSavedResponse;
import com.example.demo.dto.response.AnswersBatchSavedResponse;
import com.example.demo.dto.response.ExamOptionView;
import com.example.demo.dto.response.ExamQuestionView;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.ExamSessionResponse;
import com.example.demo.dto.response.HeartbeatResponse;
import com.example.demo.dto.response.ResultDetailView;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.RoomExamRepository;
import com.example.demo.repository.ExamQuestionAnswerRepository;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.ExamSectionRepository;
import com.example.demo.service.ExamSectionTiming;
import com.example.demo.service.JlptScoringService;
import com.example.demo.service.MistakeBookService;
import com.example.demo.service.SubmissionService;
import com.example.demo.service.cache.ExamRedisService;
import com.example.demo.service.cache.ExamRedisService.LockState;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Cài đặt vòng đời phiên làm bài. */
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionServiceImpl.class);

    private static final BigDecimal DEFAULT_POINTS = new BigDecimal("1.00");

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamQuestionAnswerRepository snapshotAnswerRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final SubmissionDetailRepository detailRepository;
    private final RoomExamRepository roomExamRepository;
    private final UserRepository userRepository;
    private final ExamRedisService examRedis;
    private final MistakeBookService mistakeBookService;
    private final JlptScoringService jlptScoringService;
    private final ExamSectionRepository examSectionRepository;

    /** Ngưỡng im lặng coi là mất kết nối — cũng chính là TTL của key nhịp sống trong Redis. */
    @Value("${exam.session.at-risk-after-seconds:90}")
    private long atRiskAfterSeconds;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public ExamSessionResponse startOrResume(Integer examId, String studentEmail) {
        User student = requireStudent(studentEmail);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        ExamGate gate = requireCanTakeExam(exam, student);
        // thí sinh làm dở
        Optional<ExamSubmission> latest = submissionRepository
                .findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(
                        examId, student.getUserId());
        if (latest.isPresent() && latest.get().isInProgress()) {
            return resumeExisting(exam, latest.get());
        }

        if (gate.notStartedReason() != null) {
            throw new BusinessException(gate.notStartedReason());
        }
        requireExamWindowOpen(exam);
        // Kiểm sớm để thí sinh hết lượt không phải chờ giành khoá.
        requireAttemptAvailable(exam, student);

        // Nhánh tạo mới cần chống hai request song song của cùng một em (double click, hai tab) cùng thấy "chưa có phiên" rồi cùng insert.
        LockState lock = examRedis.acquireStartLock(examId, student.getUserId());
        if (lock == LockState.BUSY) {
            throw new BusinessException("Yêu cầu vào phòng thi trước đó đang được xử lý, "
                    + "vui lòng thử lại sau vài giây.");
        }
        if (lock == LockState.UNAVAILABLE) {
            examRepository.findByIdForUpdate(examId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        }
        if (lock == LockState.ACQUIRED) {
            releaseStartLockAfterCommit(examId, student.getUserId());
        }

        Optional<ExamSubmission> afterLock = submissionRepository
                .findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(
                        examId, student.getUserId());
        if (afterLock.isPresent() && afterLock.get().isInProgress()) {
            return resumeExisting(exam, afterLock.get());
        }

        if (examQuestionRepository.countByExam_ExamId(examId) == 0) {
            throw new BusinessException("Đề thi chưa có câu hỏi nào, chưa thể bắt đầu");
        }

        // Đếm lại trong khoá: đây mới là con số quyết định.
        long used = requireAttemptAvailable(exam, student);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = computeExpiry(exam, now);
    
        if (gate.runningRoomEnd() != null && gate.runningRoomEnd().isBefore(expiresAt)) {
            expiresAt = gate.runningRoomEnd();
        }
        ExamSubmission session = ExamSubmission.builder()
                .exam(exam)
                .student(student)
                .attemptNumber((int) used + 1)
                .startedAt(now)
                .expiresAt(expiresAt)
                .lastActiveAt(now)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();
        submissionRepository.save(session);

        // Mở nhịp sống ngay từ lúc vào phòng, đừng đợi heartbeat đầu tiên.
        examRedis.touchAlive(session.getSubmissionId(), atRiskAfterSeconds);

        log.info("Bắt đầu phiên thi submissionId={} examId={} studentId={} lượt={}/{} expiresAt={}",
                session.getSubmissionId(), examId, student.getUserId(), session.getAttemptNumber(),
                exam.isUnlimitedAttempts() ? "∞" : exam.getMaxAttempts(), session.getExpiresAt());
        return toSessionResponse(exam, session, false);
    }

    /** Trả khoá tạo phiên, nhưng chỉ SAU khi transaction kết thúc. */
    private void releaseStartLockAfterCommit(Integer examId, String studentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            examRedis.releaseStartLock(examId, studentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                examRedis.releaseStartLock(examId, studentId);
            }
        });
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public ExamSessionResponse getSession(Integer examId, String studentEmail) {
        User student = requireStudent(studentEmail);
        ExamSubmission session = requireSession(examId, student);
        return resumeExisting(session.getExam(), session);
    }

    /** Vào lại một phiên đã tồn tại. */
    private ExamSessionResponse resumeExisting(Exam exam, ExamSubmission session) {
        if (!session.isInProgress()) {
            // Chỉ tới được đây qua GET /session — luồng start đã lọc trước và đi nhánh mở lượt mới.
            throw new BusinessException("Lượt làm thứ " + session.getAttemptNumber()
                    + " đã được nộp lúc " + session.getSubmittedAt()
                    + ". Gọi lại /start nếu bạn còn lượt làm.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (session.isExpiredAt(now)) {
            // Thí sinh mất mạng tới lúc hết giờ rồi mới quay lại: chốt bài luôn.
            finishSession(exam, session, now, true);
            throw new BusinessException("Phiên thi đã hết giờ lúc " + session.getExpiresAt()
                    + " và được nộp tự động. Xem kết quả tại /api/student/submissions/"
                    + session.getSubmissionId() + "/result");
        }

        // Quay lại được tính là còn sống -> tắt cờ nghi rớt mạng.
        markAlive(session, now);
        return toSessionResponse(exam, session, true);
    }

    /** Deadline của phiên, chốt một lần và không đổi về sau. */
    private LocalDateTime computeExpiry(Exam exam, LocalDateTime startedAt) {
        LocalDateTime byDuration = startedAt.plusMinutes(exam.getDurationMinutes());
        if (exam.getEndTime() != null && exam.getEndTime().isBefore(byDuration)) {
            return exam.getEndTime();
        }
        return byDuration;
    }

    /** Thí sinh còn lượt làm bài trên đề này không. */
    private long requireAttemptAvailable(Exam exam, User student) {
        long used = submissionRepository.countByExam_ExamIdAndStudent_UserId(
                exam.getExamId(), student.getUserId());
        if (!exam.allowsAttempt(used)) {
            throw new BusinessException("Bạn đã dùng hết " + exam.getMaxAttempts()
                    + " lượt làm bài của đề thi này. Xem lại bài làm ở mục Kết quả.");
        }
        return used;
    }

    private void requireExamWindowOpen(Exam exam) {
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new BusinessException("Đề thi chưa mở. Thời gian mở: " + exam.getStartTime());
        }
        if (exam.getEndTime() != null && !now.isBefore(exam.getEndTime())) {
            throw new BusinessException("Đề thi đã đóng lúc " + exam.getEndTime());
        }
    }

    // ── Vấn đề 3: không để mất tiến độ, và phát hiện rớt mạng ───────────────

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AnswerSavedResponse saveAnswer(Integer examId, SaveAnswerRequest request, String studentEmail) {
        User student = requireStudent(studentEmail);
        ExamSubmission session = requireSession(examId, student);
        LocalDateTime now = requireActiveSession(session);

        SubmissionDetail detail = upsertAnswer(examId, session, request, now);

        markAlive(session, now);

        return AnswerSavedResponse.builder()
                .submissionId(session.getSubmissionId())
                .questionId(request.getQuestionId())
                .answeredAt(detail.getAnsweredAt())
                .serverTime(now)
                .expiresAt(session.getExpiresAt())
                .remainingSeconds(session.remainingSeconds(now))
                .answeredQuestions(countAnswered(session))
                .build();
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AnswersBatchSavedResponse saveAnswers(Integer examId, SaveAnswersBatchRequest request,
                                                 String studentEmail) {
        User student = requireStudent(studentEmail);
        ExamSubmission session = requireSession(examId, student);
        if (request.getSubmissionId() != null
                && !request.getSubmissionId().equals(session.getSubmissionId())) {
            throw new BusinessException("Lô đáp án thuộc lượt làm id=" + request.getSubmissionId()
                    + ", lượt đó đã đóng. Không ghi vào lượt hiện tại.");
        }
        LocalDateTime now = requireActiveSession(session);

        // Gom theo câu trước khi ghi: client có thể gửi cùng một câu hai lần (thí sinh đổi ý giữa hai nhịp gửi)
        Map<Integer, SaveAnswerRequest> latest = new LinkedHashMap<>();
        for (SaveAnswerRequest answer : request.getAnswers()) {
            latest.put(answer.getQuestionId(), answer);
        }

        // Kiểm hết cả lô rồi mới ghi câu nào.
        ExamSectionTiming timing = timingOf(examId, session);
        List<ResolvedAnswer> resolved = new ArrayList<>(latest.size());
        for (SaveAnswerRequest answer : latest.values()) {
            resolved.add(resolveAnswer(examId, answer, timing, now));
        }
        for (ResolvedAnswer answer : resolved) {
            writeAnswer(session, answer, now);
        }

        markAlive(session, now);

        return AnswersBatchSavedResponse.builder()
                .submissionId(session.getSubmissionId())
                .savedQuestionIds(new ArrayList<>(latest.keySet()))
                .answeredAt(now)
                .serverTime(now)
                .expiresAt(session.getExpiresAt())
                .remainingSeconds(session.remainingSeconds(now))
                .answeredQuestions(countAnswered(session))
                .build();
    }

    /** Ghi đáp án của một câu theo kiểu upsert trên (SubmissionID, QuestionID). */
    private SubmissionDetail upsertAnswer(Integer examId, ExamSubmission session,
                                          SaveAnswerRequest request, LocalDateTime now) {
        return writeAnswer(session, resolveAnswer(examId, request, timingOf(examId, session), now), now);
    }

    /** Lịch chạy các phần của một phiên. */
    private ExamSectionTiming timingOf(Integer examId, ExamSubmission session) {
        return new ExamSectionTiming(session.getStartedAt(),
                examSectionRepository.findByExam_ExamIdOrderByOrderNoAsc(examId));
    }

    /** Một đáp án đã qua kiểm tra, sẵn sàng ghi. */
    private record ResolvedAnswer(SaveAnswerRequest request, ExamQuestion examQuestion,
                                  ExamQuestionAnswer selected) {
    }

    /** Bước kiểm tra của upsert: chỉ đọc, không ghi gì, ném lỗi nếu đáp án không hợp lệ. */
    private ResolvedAnswer resolveAnswer(Integer examId, SaveAnswerRequest request,
                                         ExamSectionTiming timing, LocalDateTime now) {
        ExamQuestion examQuestion = examQuestionRepository
                .findByExam_ExamIdAndQuestion_QuestionId(examId, request.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi id=" + request.getQuestionId()
                        + " không thuộc đề thi id=" + examId));

        // Hết giờ một phần là KHOÁ phần đó.
        if (timing != null && timing.hasSections()) {
            Integer sectionId = examQuestion.getSection() == null
                    ? null : examQuestion.getSection().getSectionId();
            if (!timing.isOpen(sectionId, now)) {
                throw new BusinessException(timing.closedReason(sectionId, now));
            }
        }

        return new ResolvedAnswer(request, examQuestion,
                resolveSelectedOption(examId, examQuestion, request));
    }

    /** Bước ghi của upsert. */
    private SubmissionDetail writeAnswer(ExamSubmission session, ResolvedAnswer answer, LocalDateTime now) {
        SaveAnswerRequest request = answer.request();
        ExamQuestionAnswer selected = answer.selected();

        SubmissionDetail detail = detailRepository
                .findBySubmission_SubmissionIdAndQuestion_QuestionId(
                        session.getSubmissionId(), request.getQuestionId())
                .orElseGet(() -> SubmissionDetail.builder()
                        .submission(session)
                        .question(answer.examQuestion().getQuestion())
                        .build());

        detail.setSelectedSnapshotAnswer(selected);
        // Cột truy vết về ngân hàng câu hỏi; null nếu đáp án gốc đã bị xoá.
        detail.setSelectedAnswer(selected == null ? null : selected.getOriginalAnswer());
        detail.setEssayResponse(trimToNull(request.getEssayResponse()));
        detail.setIsCorrect(false);
        detail.setScoreEarned(BigDecimal.ZERO);
        detail.setAnsweredAt(now);
        return detailRepository.save(detail);
    }

    /** Kiểm tra đáp án gửi lên có thật là một lựa chọn của đúng câu hỏi đó trong đúng đề đó. */
    private ExamQuestionAnswer resolveSelectedOption(Integer examId, ExamQuestion examQuestion,
                                                     SaveAnswerRequest request) {
        if (request.getSnapshotAnswerId() == null) {
            return null;    
        }
        if (examQuestion.resolveType() == QuestionType.ESSAY) {
            throw new BusinessException("Câu tự luận id=" + request.getQuestionId()
                    + " không nhận snapshotAnswerId");
        }
        ExamQuestionAnswer option = snapshotAnswerRepository
                .findBySnapshotAnswerIdAndExamQuestion_Exam_ExamId(request.getSnapshotAnswerId(), examId)
                .orElseThrow(() -> new ResourceNotFoundException("Đáp án id=" + request.getSnapshotAnswerId()
                        + " không thuộc đề thi id=" + examId));
        if (!option.getExamQuestion().getId().getQuestionId().equals(request.getQuestionId())) {
            throw new BusinessException("Đáp án id=" + request.getSnapshotAnswerId()
                    + " không thuộc câu hỏi id=" + request.getQuestionId());
        }
        return option;
    }

    @Override
    @Transactional
    public HeartbeatResponse heartbeat(Integer examId, String studentEmail) {
        User student = requireStudent(studentEmail);
        ExamSubmission session = requireSession(examId, student);
        LocalDateTime now = LocalDateTime.now();
        boolean wasAtRisk = Boolean.TRUE.equals(session.getAtRiskStatus());
        boolean justAutoSubmitted = false;

        if (session.isInProgress()) {
            if (session.isExpiredAt(now)) {
                finishSession(session.getExam(), session, now, true);
                justAutoSubmitted = true;
            } else {
                markAlive(session, now);
            }
        }

        return HeartbeatResponse.builder()
                .submissionId(session.getSubmissionId())
                .status(session.getStatus())
                .serverTime(now)
                .expiresAt(session.getExpiresAt())
                .remainingSeconds(session.remainingSeconds(now))
                .recoveredFromAtRisk(wasAtRisk)
                .autoSubmitted(justAutoSubmitted)
                .build();
    }

    // hết giờ là bài nộp

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public ExamResultResponse submit(Integer examId, SubmitExamRequest request, String studentEmail) {
        User student = requireStudent(studentEmail);
        ExamSubmission session = requireSession(examId, student);
        if (!session.isInProgress()) {
            throw new BusinessException("Bài thi này đã được nộp lúc " + session.getSubmittedAt() + ".");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean expired = session.isExpiredAt(now);

        // Lưới an toàn cho các câu autosave chưa kịp gửi lên.
        if (!expired && request != null && request.getAnswers() != null) {
            // Cùng luật khoá phần như autosave: câu thuộc phần đã hết giờ thì không được len vào lúc nộp bài.
            ExamSectionTiming timing = timingOf(examId, session);
            List<ResolvedAnswer> resolved = new ArrayList<>();
            for (SaveAnswerRequest answer : request.getAnswers()) {
                if (answer.getQuestionId() == null) {
                    continue;
                }
                try {
                    resolved.add(resolveAnswer(examId, answer, timing, now));
                } catch (BusinessException e) {
                    log.debug("Bỏ qua câu quá hạn lúc nộp bài: {}", e.getMessage());
                }
            }
            for (ResolvedAnswer answer : resolved) {
                writeAnswer(session, answer, now);
            }
        }
        return finishSession(session.getExam(), session, now, expired);
    }

    @Override
    @Transactional
    public int autoSubmitExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<ExamSubmission> expired = submissionRepository
                .findByStatusAndExpiresAtLessThanEqual(SubmissionStatus.IN_PROGRESS, now);
        // autosubmit
        for (ExamSubmission session : expired) {
            finishSession(session.getExam(), session, now, true);
        }
        if (!expired.isEmpty()) {
            log.info("Đã tự động nộp {} bài quá giờ", expired.size());
        }
        return expired.size();
    }

    @Override
    @Transactional
    public int flagDisconnectedSessions(long silenceSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(silenceSeconds);
        List<ExamSubmission> candidates = submissionRepository
                .findByStatusAndAtRiskStatusFalseAndLastActiveAtLessThan(
                        SubmissionStatus.IN_PROGRESS, threshold);
        if (candidates.isEmpty()) {
            return 0;
        }
        Optional<Set<Integer>> alive = examRedis.findAlive(
                candidates.stream().map(ExamSubmission::getSubmissionId).toList());

        int flagged = 0;
        for (ExamSubmission session : candidates) {
            if (alive.isPresent() && alive.get().contains(session.getSubmissionId())) {
                continue;
            }
            session.setAtRiskStatus(true);
            flagged++;
        }
        if (flagged > 0) {
            log.info("Đánh dấu AtRiskStatus cho {}/{} phiên im lặng quá {} giây",
                    flagged, candidates.size(), silenceSeconds);
        }
        return flagged;
    }

    private void markAlive(ExamSubmission session, LocalDateTime now) {
        if (examRedis.touchAlive(session.getSubmissionId(), atRiskAfterSeconds)) {
            session.setLastActiveAt(now);
        }
        session.setAtRiskStatus(false);
    }

    /** Chốt một phiên thi: chấm các câu trắc nghiệm theo snapshot đáp án của đề, rồi đóng phiên lại. */
    private ExamResultResponse finishSession(Exam exam, ExamSubmission session,
                                             LocalDateTime now, boolean auto) {
        List<ExamQuestion> examQuestions =
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId());
        Map<Integer, SubmissionDetail> details = detailsOf(session);

        BigDecimal total = BigDecimal.ZERO;
        boolean awaitingManual = false;
        // Câu làm sai được  lại để đẩy sang sổ tay câu sai sau khi chấm xong.
        List<Integer> wrongQuestionIds = new ArrayList<>();

        for (ExamQuestion examQuestion : examQuestions) {
            Integer questionId = examQuestion.getId().getQuestionId();
            SubmissionDetail detail = details.get(questionId);
            if (detail == null) {
                detail = SubmissionDetail.builder()
                        .submission(session)
                        .question(examQuestion.getQuestion())
                        .build();
                details.put(questionId, detail);
            }

            if (examQuestion.resolveType() == QuestionType.ESSAY) {
                detail.setIsCorrect(false);
                detail.setScoreEarned(BigDecimal.ZERO);
                if (detail.getEssayResponse() != null) {
                    awaitingManual = true;
                }
            } else {
                ExamQuestionAnswer chosen = detail.getSelectedSnapshotAnswer();
                boolean correct = chosen != null && Boolean.TRUE.equals(chosen.getIsCorrect());
                BigDecimal earned = correct ? pointsOf(examQuestion) : BigDecimal.ZERO;
                detail.setIsCorrect(correct);
                detail.setScoreEarned(earned);
                total = total.add(earned);
                if (!correct) {
                    wrongQuestionIds.add(questionId);
                }
            }
            detailRepository.save(detail);
        }

        session.setTotalScore(total);
        session.setSubmittedAt(now);
        session.setAutoSubmitted(auto);
        session.setLastActiveAt(now);
        session.setAtRiskStatus(false);

        session.setStatus(awaitingManual ? SubmissionStatus.SUBMITTED : SubmissionStatus.GRADED);
        submissionRepository.save(session);

    
        examRedis.clearSession(session.getSubmissionId());

        // Đẩy câu sai sang sổ tay ôn tập.
        try {
            mistakeBookService.recordMistakes(session.getStudent(), wrongQuestionIds, now);
        } catch (Exception e) {
            log.error("Không ghi được sổ tay câu sai cho submissionId={}",
                    session.getSubmissionId(), e);
        }

        log.info("Chốt bài submissionId={} examId={} auto={} score={} status={}",
                session.getSubmissionId(), exam.getExamId(), auto, total, session.getStatus());
        return buildResult(exam, session, examQuestions, details,
                Boolean.TRUE.equals(exam.getAllowReview()), true);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamResultResponse getResult(Integer submissionId, String studentEmail) {
        User student = requireStudent(studentEmail);
        ExamSubmission session = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bài làm id=" + submissionId));
        if (!session.getStudent().getUserId().equals(student.getUserId())) {
            throw new UnauthorizedException("Bài làm này không thuộc về bạn");
        }
        if (session.isInProgress()) {
            throw new BusinessException("Bài thi chưa nộp nên chưa có kết quả");
        }
        Exam exam = session.getExam();

        boolean reveal = Boolean.TRUE.equals(exam.getAllowReview());
        ExamResultResponse result = buildResult(exam, session,
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId()),
                detailsOf(session), reveal, true);

        long used = submissionRepository.countByExam_ExamIdAndStudent_UserId(
                exam.getExamId(), student.getUserId());
        result.setAttemptsUsed(used);
        result.setCanRetake(exam.allowsAttempt(used));
        return result;
    }

    @Override
    @Transactional
    public AudioPlayResponse recordAudioPlay(Integer examId, Integer questionId, String studentEmail) {
        User student = requireStudent(studentEmail);
        ExamSubmission session = requireSession(examId, student);
        LocalDateTime now = requireActiveSession(session);

        ExamQuestion examQuestion = examQuestionRepository
                .findByExam_ExamIdAndQuestion_QuestionId(examId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi id=" + questionId
                        + " không thuộc đề thi id=" + examId));
        String audioUrl = examQuestion.resolveAudioUrl();
        if (audioUrl == null) {
            throw new BusinessException("Câu này không có file nghe");
        }

        ExamSectionTiming timing = timingOf(examId, session);
        Integer sectionId = examQuestion.getSection() == null ? null : examQuestion.getSection().getSectionId();
        if (timing.hasSections() && !timing.isOpen(sectionId, now)) {
            throw new BusinessException(timing.closedReason(sectionId, now));
        }

        int max = examQuestion.resolveMaxAudioPlays();
        SubmissionDetail detail = detailRepository
                .findBySubmission_SubmissionIdAndQuestion_QuestionId(session.getSubmissionId(), questionId)
                .orElseGet(() -> SubmissionDetail.builder()
                        .submission(session)
                        .question(examQuestion.getQuestion())
                        .build());
        int played = detail.getAudioPlays() == null ? 0 : detail.getAudioPlays();
        if (played >= max) {
            throw new BusinessException("Bạn đã nghe hết " + max + " lượt cho câu này.");
        }
        detail.setAudioPlays(played + 1);
        detailRepository.save(detail);
        markAlive(session, now);

        return AudioPlayResponse.builder()
                .questionId(questionId)
                .audioUrl(audioUrl)
                .plays(played + 1)
                .maxPlays(max)
                .remaining(max - played - 1)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamResultResponse getPaperForReview(Integer submissionId) {
        ExamSubmission session = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bài làm id=" + submissionId));
        if (session.isInProgress()) {
            throw new BusinessException("Thí sinh chưa nộp bài này nên chưa có gì để xem");
        }
        Exam exam = session.getExam();
        ExamResultResponse result = buildResult(exam, session,
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId()),
                detailsOf(session), true, true);
        result.setAttemptsUsed(submissionRepository.countByExam_ExamIdAndStudent_UserId(
                exam.getExamId(), session.getStudent().getUserId()));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamResultResponse> getHistory(String studentEmail) {
        User student = requireStudent(studentEmail);
        List<ExamResultResponse> history = new ArrayList<>();
        for (ExamSubmission session : submissionRepository.findByStudentUserId(student.getUserId())) {
            if (session.isInProgress()) {
                continue;   // phiên đang làm dở không phải "kết quả"
            }
            Exam exam = session.getExam();
            // Danh sách lịch sử không kèm chi tiết từng câu và không lộ đáp án —
            // đáp án chỉ ra ở trang xem lại một bài cụ thể.
            history.add(buildResult(exam, session,
                    examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId()),
                    detailsOf(session), false, false));
        }
        // Bài mới nộp lên đầu. Một đề giờ có thể có nhiều lượt.
        history.sort(Comparator
                .comparing(ExamResultResponse::getSubmittedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ExamResultResponse::getAttemptNumber,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return history;
    }

    /** Gói trạng thái phiên thi cho client. */
    private ExamSessionResponse toSessionResponse(Exam exam, ExamSubmission session, boolean resumed) {
        LocalDateTime now = LocalDateTime.now();
        // Phần đề (nội dung câu hỏi + lựa chọn) giống nhau với mọi thí sinh nên lấy từ cache Redis.
        List<ExamQuestionView> questions = loadPaper(exam.getExamId());
        Map<Integer, SubmissionDetail> details = detailsOf(session);
        ExamSectionTiming timing = timingOf(exam.getExamId(), session);

        int answered = 0;
        // Đếm riêng theo từng phần để thanh phần thi nói được "3/12 câu"
        Map<Integer, Integer> totalBySection = new LinkedHashMap<>();
        Map<Integer, Integer> answeredBySection = new LinkedHashMap<>();

        for (ExamQuestionView question : questions) {
            SubmissionDetail detail = details.get(question.getQuestionId());
            Integer selectedId = selectedIdOf(detail);
            String essay = detail == null ? null : detail.getEssayResponse();
            boolean hasAnswer = selectedId != null || essay != null;
            if (hasAnswer) {
                answered++;
            }
            if (question.getSectionId() != null) {
                totalBySection.merge(question.getSectionId(), 1, Integer::sum);
                if (hasAnswer) {
                    answeredBySection.merge(question.getSectionId(), 1, Integer::sum);
                }
            }
            // Ghép bài làm của em này vào bản đề vừa lấy.
            question.setSelectedSnapshotAnswerId(selectedId);
            question.setEssayResponse(essay);
            question.setAnsweredAt(detail == null ? null : detail.getAnsweredAt());
            question.setAudioPlays(detail == null || detail.getAudioPlays() == null ? 0 : detail.getAudioPlays());
        }

        return ExamSessionResponse.builder()
                .submissionId(session.getSubmissionId())
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .attemptNumber(session.getAttemptNumber())
                .maxAttempts(exam.getMaxAttempts())
                .status(session.getStatus())
                .resumed(resumed)
                .startedAt(session.getStartedAt())
                .expiresAt(session.getExpiresAt())
                .serverTime(now)
                .remainingSeconds(session.remainingSeconds(now))
                .atRisk(Boolean.TRUE.equals(session.getAtRiskStatus()))
                .totalQuestions(questions.size())
                .answeredQuestions(answered)
                // Xáo theo hạt giống là submissionId.
                .questions(PaperShuffler.shuffle(questions,
                        Boolean.TRUE.equals(exam.getShuffleQuestions()),
                        Boolean.TRUE.equals(exam.getShuffleOptions()),
                        session.getSubmissionId()))
                // Rỗng với đề không chia phần — client hiểu là chạy một đồng hồ
                // duy nhất, y như trước v1.8.0.
                .sections(timing.hasSections()
                        ? timing.toViews(now, totalBySection, answeredBySection)
                        : List.of())
                .build();
    }

    private ExamResultResponse buildResult(Exam exam, ExamSubmission session,
                                           List<ExamQuestion> examQuestions,
                                           Map<Integer, SubmissionDetail> details,
                                           boolean revealAnswers, boolean includeDetails) {
        List<ResultDetailView> views = new ArrayList<>();
        BigDecimal maxScore = BigDecimal.ZERO;
        int answered = 0;
        int correct = 0;
        boolean awaitingManual = false;

        for (ExamQuestion examQuestion : examQuestions) {
            Integer questionId = examQuestion.getId().getQuestionId();
            SubmissionDetail detail = details.get(questionId);
            boolean essay = examQuestion.resolveType() == QuestionType.ESSAY;
            Integer selectedId = selectedIdOf(detail);
            String essayResponse = detail == null ? null : detail.getEssayResponse();
            boolean hasAnswer = selectedId != null || essayResponse != null;

            maxScore = maxScore.add(pointsOf(examQuestion));
            if (hasAnswer) {
                answered++;
            }
            if (detail != null && Boolean.TRUE.equals(detail.getIsCorrect())) {
                correct++;
            }
            boolean awaiting = essay && hasAnswer;
            if (awaiting) {
                awaitingManual = true;
            }
            if (!includeDetails) {
                continue;
            }

            // Một lần đọc snapshot cho cả ba thứ trang xem lại cần: danh sách
            // lựa chọn, id đáp án đúng, nội dung đáp án đúng.
            List<ExamQuestionAnswer> snapshot = essay ? List.of()
                    : snapshotAnswerRepository
                    .findByExamQuestion_Exam_ExamIdAndExamQuestion_Question_QuestionIdOrderByAnswerOrderAsc(
                            exam.getExamId(), questionId);
            ExamQuestionAnswer correctOption = snapshot.stream()
                    .filter(option -> Boolean.TRUE.equals(option.getIsCorrect()))
                    .findFirst()
                    .orElse(null);

            views.add(ResultDetailView.builder()
                    .questionId(questionId)
                    .questionOrder(examQuestion.getQuestionOrder())
                    .content(examQuestion.resolveContent())
                    .questionType(examQuestion.resolveType())
                    .points(pointsOf(examQuestion))
                    .selectedSnapshotAnswerId(selectedId)
                    .selectedAnswerContent(detail == null || detail.getSelectedSnapshotAnswer() == null
                            ? null : detail.getSelectedSnapshotAnswer().getAnswerContent())
                    .essayResponse(essayResponse)
                    .correct(detail == null ? Boolean.FALSE : detail.getIsCorrect())
                    .scoreEarned(detail == null ? BigDecimal.ZERO : detail.getScoreEarned())
                    // Các lựa chọn hiện cả khi đề tắt xem đáp án.
                    .options(snapshot.stream()
                            .map(option -> ExamOptionView.builder()
                                    .snapshotAnswerId(option.getSnapshotAnswerId())
                                    .answerContent(option.getAnswerContent())
                                    .answerOrder(option.getAnswerOrder())
                                    .build())
                            .toList())
                    .correctSnapshotAnswerId(revealAnswers && correctOption != null
                            ? correctOption.getSnapshotAnswerId() : null)
                    .correctAnswerContent(revealAnswers && correctOption != null
                            ? correctOption.getAnswerContent() : null)
                    .explanation(revealAnswers ? examQuestion.getExplanation() : null)
                    .awaitingManualGrading(awaiting)
                    .build());
        }

        return ExamResultResponse.builder()
                .submissionId(session.getSubmissionId())
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
                .attemptNumber(session.getAttemptNumber())
                // Cờ của ĐỀ, không phải của payload này.
                .reviewAllowed(Boolean.TRUE.equals(exam.getAllowReview()))
                .maxAttempts(exam.getMaxAttempts())
                .status(session.getStatus())
                .autoSubmitted(Boolean.TRUE.equals(session.getAutoSubmitted()))
                .startedAt(session.getStartedAt())
                .submittedAt(session.getSubmittedAt())
                .totalScore(session.getTotalScore())
                .maxScore(maxScore)
                .totalQuestions(examQuestions.size())
                .answeredQuestions(answered)
                .correctAnswers(correct)
                .awaitingManualGrading(awaitingManual)
                .details(includeDetails ? views : null)
                // Trả null khi đề không chấm theo JLPT — xem JlptScoringService.
                .jlpt(jlptScoringService.score(exam, examQuestions, details))
                .build();
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ thí sinh mới được làm bài thi");
        }
        return user;
    }

    /** Kết quả kiểm quyền làm một đề. */
    private record ExamGate(LocalDateTime runningRoomEnd, String notStartedReason) {
        static final ExamGate FREE = new ExamGate(null, null);
    }

    /** Thí sinh có được làm đề này không. */
    private ExamGate requireCanTakeExam(Exam exam, User student) {
        if (Boolean.TRUE.equals(exam.getIsPublic())) {
            return ExamGate.FREE;
        }
        List<Room> rooms = roomExamRepository.findRoomsForActiveMember(
                exam.getExamId(), student.getUserId());
        if (rooms.isEmpty()) {
            // Trả 404 chứ không 403: không tiết lộ đề tồn tại cho người ngoài phòng.
            throw new ResourceNotFoundException("Không tìm thấy đề thi id=" + exam.getExamId());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime runningEnd = null;
        Room waiting = null;
        for (Room room : rooms) {
            Integer longest = roomExamRepository.findLongestDurationMinutes(room.getRoomId());
            RoomPhase phase = room.phaseAt(now, longest);
            if (phase == RoomPhase.IN_PROGRESS) {
                LocalDateTime end = room.endAt(longest);
                if (runningEnd == null || (end != null && end.isAfter(runningEnd))) {
                    runningEnd = end;
                }
            } else if (phase == RoomPhase.WAITING && waiting == null) {
                waiting = room;
            }
        }
        if (runningEnd != null) {
            return new ExamGate(runningEnd, null);
        }
        if (waiting != null) {
            return new ExamGate(null, "Phòng \"" + waiting.getName() + "\" chưa bắt đầu làm bài. "
                    + (waiting.getStartTime() != null
                            ? "Giờ bắt đầu: " + waiting.getStartTime() + "."
                            : "Chờ người ra đề bấm \"Bắt đầu làm bài\"."));
        }
        return new ExamGate(null, "Phòng thi đã hết giờ làm bài. Xem bảng xếp hạng ở trang phòng thi.");
    }

    /** Phiên mà mọi thao tác trong phòng thi tác động lên: LƯỢT GẦN NHẤT. */
    private ExamSubmission requireSession(Integer examId, User student) {
        return submissionRepository
                .findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(
                        examId, student.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bạn chưa bắt đầu làm đề thi id=" + examId));
    }

    /** Phiên phải đang mở mới cho ghi. */
    private LocalDateTime requireActiveSession(ExamSubmission session) {
        if (!session.isInProgress()) {
            throw new BusinessException("Bài thi này đã nộp, không thể thay đổi đáp án.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.isExpiredAt(now)) {
            finishSession(session.getExam(), session, now, true);
            throw new BusinessException("Đã hết giờ làm bài lúc " + session.getExpiresAt()
                    + ". Bài của bạn đã được nộp tự động.");
        }
        return now;
    }

    /** Các câu đã trả lời của một phiên, tra theo QuestionID. Map cho phép ghi thêm. */
    private Map<Integer, SubmissionDetail> detailsOf(ExamSubmission session) {
        Map<Integer, SubmissionDetail> map = new LinkedHashMap<>();
        for (SubmissionDetail detail : detailRepository
                .findBySubmission_SubmissionId(session.getSubmissionId())) {
            map.put(detail.getQuestion().getQuestionId(), detail);
        }
        return map;
    }

    private int countAnswered(ExamSubmission session) {
        int answered = 0;
        for (SubmissionDetail detail : detailsOf(session).values()) {
            if (detail.getSelectedSnapshotAnswer() != null || detail.getEssayResponse() != null) {
                answered++;
            }
        }
        return answered;
    }

    /** Bản đề để thí sinh làm bài: câu hỏi theo thứ tự, kèm các lựa chọn. */
    private List<ExamQuestionView> loadPaper(Integer examId) {
        Optional<List<ExamQuestionView>> cached = examRedis.getPaper(examId);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<ExamQuestionView> paper = new ArrayList<>();
        for (ExamQuestion examQuestion : examQuestionRepository
                .findByExam_ExamIdOrderByQuestionOrderAsc(examId)) {
            Integer questionId = examQuestion.getId().getQuestionId();
            paper.add(ExamQuestionView.builder()
                    .questionId(questionId)
                    .questionOrder(examQuestion.getQuestionOrder())
                    .points(pointsOf(examQuestion))
                    .content(examQuestion.resolveContent())
                    .questionType(examQuestion.resolveType())
                    .options(optionsOf(examId, questionId, examQuestion.resolveType()))
                    .sectionId(examQuestion.getSection() == null
                            ? null : examQuestion.getSection().getSectionId())
                    .skill(examQuestion.resolveSkill())
                    // Đoạn văn đi theo từng câu, nhưng client gộp lại theo
                    // passageId nên thí sinh chỉ đọc nó một lần.
                    .passageId(examQuestion.getPassageId())
                    .passageTitle(examQuestion.getPassageTitle())
                    .passageContent(examQuestion.getPassageContent())
                    .audioUrl(examQuestion.resolveAudioUrl())
                    .maxAudioPlays(examQuestion.resolveAudioUrl() == null
                            ? null : examQuestion.resolveMaxAudioPlays())
                    .build());
        }
        // Ghi cache TRƯỚC khi phía gọi ghép bài làm vào: cái được cất đi phải là
        // bản đề trắng, không dính đáp án của em vừa gọi.
        examRedis.putPaper(examId, paper);
        return paper;
    }

    /** Các lựa chọn hiển thị cho thí sinh. Không bao giờ kèm cờ đáp án đúng. */
    private List<ExamOptionView> optionsOf(Integer examId, Integer questionId, QuestionType type) {
        if (type == QuestionType.ESSAY) {
            return List.of();
        }
        List<ExamOptionView> options = new ArrayList<>();
        for (ExamQuestionAnswer answer : snapshotAnswerRepository
                .findByExamQuestion_Exam_ExamIdAndExamQuestion_Question_QuestionIdOrderByAnswerOrderAsc(
                        examId, questionId)) {
            options.add(ExamOptionView.builder()
                    .snapshotAnswerId(answer.getSnapshotAnswerId())
                    .answerContent(answer.getAnswerContent())
                    .answerOrder(answer.getAnswerOrder())
                    .build());
        }
        return options;
    }

    private Integer selectedIdOf(SubmissionDetail detail) {
        return detail == null || detail.getSelectedSnapshotAnswer() == null
                ? null : detail.getSelectedSnapshotAnswer().getSnapshotAnswerId();
    }

    private BigDecimal pointsOf(ExamQuestion examQuestion) {
        return examQuestion.getPoints() != null ? examQuestion.getPoints() : DEFAULT_POINTS;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
