package com.example.demo.service.impl;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.ClassEntity;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamQuestionAnswer;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.SubmissionDetail;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.SaveAnswerRequest;
import com.example.demo.dto.request.SubmitExamRequest;
import com.example.demo.dto.response.AnswerSavedResponse;
import com.example.demo.dto.response.ExamOptionView;
import com.example.demo.dto.response.ExamQuestionView;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.ExamSessionResponse;
import com.example.demo.dto.response.HeartbeatResponse;
import com.example.demo.dto.response.ResultDetailView;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.ExamQuestionAnswerRepository;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cài đặt vòng đời phiên làm bài.
 *
 * Ghi chú về {@code noRollbackFor = BusinessException.class}: mấy chỗ phát hiện
 * "đã hết giờ" sẽ nộp bài tự động rồi mới ném BusinessException để client biết
 * mà chuyển trang. Nếu để exception đó rollback thì việc nộp bài vừa làm cũng
 * bị hoàn tác, học sinh gọi lại lần nữa lại rơi vào đúng nhánh đó — nên các
 * method này khai báo không rollback với BusinessException. Mọi BusinessException
 * trong lớp này đều được ném ở vị trí không có thay đổi nào cần huỷ.
 *
 * Ghi chú về Redis ({@link ExamRedisService}): MySQL vẫn là nguồn sự thật duy
 * nhất của phiên thi. Redis chỉ gánh ba việc mà DB làm thì tốn kém:
 *
 *   - Cache đề thi đã snapshot, để mỗi lần vào phòng thi không phải dựng lại
 *     danh sách câu hỏi + lựa chọn bằng hàng chục query.
 *   - Khoá hẹp theo (đề, học sinh) lúc tạo phiên, thay cho khoá dòng đề thi vốn
 *     bắt cả lớp xếp hàng đúng lúc vào thi.
 *   - Nhịp sống (presence) của học sinh, để heartbeat 15 giây một lần không biến
 *     thành một UPDATE xuống DB mỗi lần.
 *
 * Mất Redis không làm hỏng kì thi: mọi lối gọi đều có đường lui về MySQL, đúng
 * bằng hành vi trước khi có Redis — chỉ chậm hơn.
 */
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
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final ExamRedisService examRedis;

    /**
     * Ngưỡng im lặng coi là mất kết nối — cũng chính là TTL của key nhịp sống
     * trong Redis. Dùng chung một giá trị với {@code ExamSessionScheduler} để
     * key hết hạn đúng lúc job đi quét, không lệch nhau.
     */
    @Value("${exam.session.at-risk-after-seconds:90}")
    private long atRiskAfterSeconds;

    // ── Vấn đề 1: một học sinh — một đề — một phiên ─────────────────────────

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public ExamSessionResponse startOrResume(Integer examId, String studentEmail) {
        User student = requireStudent(studentEmail);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        requireEnrolled(exam, student);

        // Nhánh nhanh: đã có phiên thì đây là "vào lại phòng thi", không khoá gì.
        Optional<ExamSubmission> existing = submissionRepository
                .findByExam_ExamIdAndStudent_UserId(examId, student.getUserId());
        if (existing.isPresent()) {
            return resumeExisting(exam, existing.get());
        }

        requireExamWindowOpen(exam);

        // Nhánh tạo mới cần chống hai request song song của cùng một em (double
        // click, hai tab) cùng thấy "chưa có phiên" rồi cùng insert.
        //
        // Khoá Redis hẹp theo (đề, học sinh) nên cả lớp bấm "Bắt đầu" cùng lúc
        // vẫn chạy song song. Chỉ khi Redis không dùng được mới quay về khoá
        // dòng đề thi dưới DB — đúng hành vi cũ, chậm nhưng vẫn an toàn.
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
        // Nhánh tạo mới: khoá dòng đề thi để hai request song song (double-click,
        // hai tab) không cùng lúc thấy "chưa có phiên" rồi cùng insert.
        examRepository.findByIdForUpdate(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));

        Optional<ExamSubmission> afterLock = submissionRepository
                .findByExam_ExamIdAndStudent_UserId(examId, student.getUserId());
        if (afterLock.isPresent()) {
            return resumeExisting(exam, afterLock.get());
        }

        if (examQuestionRepository.countByExam_ExamId(examId) == 0) {
            throw new BusinessException("Đề thi chưa có câu hỏi nào, chưa thể bắt đầu");
        }

        LocalDateTime now = LocalDateTime.now();
        ExamSubmission session = ExamSubmission.builder()
                .exam(exam)
                .student(student)
                .startedAt(now)
                .expiresAt(computeExpiry(exam, now))
                .lastActiveAt(now)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();
        submissionRepository.save(session);

        // Mở nhịp sống ngay từ lúc vào phòng, đừng đợi heartbeat đầu tiên: nếu
        // không, phiên vừa tạo đã bị job quét coi là mất kết nối.
        examRedis.touchAlive(session.getSubmissionId(), atRiskAfterSeconds);

        log.info("Bắt đầu phiên thi submissionId={} examId={} studentId={} expiresAt={}",
                session.getSubmissionId(), examId, student.getUserId(), session.getExpiresAt());
        return toSessionResponse(exam, session, false);
    }

    /**
     * Trả khoá tạo phiên, nhưng chỉ SAU khi transaction kết thúc.
     *
     * Trả ngay trong thân method là sai: lúc đó dòng ExamSubmissions vừa insert
     * chưa commit, request thứ hai giành được khoá sẽ không thấy nó (READ
     * COMMITTED) và insert thêm một phiên nữa. Đợi tới afterCompletion thì
     * request sau chắc chắn đọc được phiên vừa tạo và đi nhánh "vào lại phòng".
     *
     * Trả cả khi transaction rollback — khi đó không có phiên nào được tạo nên
     * học sinh phải được thử lại ngay, không phải chờ hết TTL.
     */
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

    /**
     * Vào lại một phiên đã tồn tại. Đây là điểm hội tụ của hai luồng "bắt đầu
     * thi" và "khôi phục sau khi mất mạng" — cả hai đều đi qua đây nên client
     * chỉ cần một cách xử lý.
     */
    private ExamSessionResponse resumeExisting(Exam exam, ExamSubmission session) {
        if (!session.isInProgress()) {
            throw new BusinessException("Bạn đã nộp bài đề thi này lúc " + session.getSubmittedAt()
                    + ". Mỗi đề chỉ được làm một lần.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (session.isExpiredAt(now)) {
            // Học sinh mất mạng tới lúc hết giờ rồi mới quay lại: chốt bài luôn.
            finishSession(exam, session, now, true);
            throw new BusinessException("Phiên thi đã hết giờ lúc " + session.getExpiresAt()
                    + " và được nộp tự động. Xem kết quả tại /api/student/submissions/"
                    + session.getSubmissionId() + "/result");
        }

        // Quay lại được tính là còn sống -> tắt cờ nghi rớt mạng.
        markAlive(session, now);
        session.setLastActiveAt(now);
        session.setAtRiskStatus(false);
        return toSessionResponse(exam, session, true);
    }

    /**
     * Deadline của phiên, chốt một lần và không đổi về sau.
     * Lấy mốc sớm hơn giữa "đủ số phút làm bài" và "giờ đóng đề".
     */
    private LocalDateTime computeExpiry(Exam exam, LocalDateTime startedAt) {
        LocalDateTime byDuration = startedAt.plusMinutes(exam.getDurationMinutes());
        if (exam.getEndTime() != null && exam.getEndTime().isBefore(byDuration)) {
            return exam.getEndTime();
        }
        return byDuration;
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
        session.setLastActiveAt(now);
        session.setAtRiskStatus(false);

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

    /**
     * Ghi đáp án của một câu theo kiểu upsert trên (SubmissionID, QuestionID).
     *
     * Đây là chỗ giải quyết chuyện mất tiến độ: học sinh vừa bấm chọn là dữ liệu
     * đã nằm trong DB, không đợi tới lúc nộp bài. Gọi lại nhiều lần cho cùng một
     * câu chỉ ghi đè dòng cũ.
     *
     * Cố tình KHÔNG chấm điểm ở đây — IsCorrect/ScoreEarned chỉ được tính lúc
     * chốt bài, để không có đường nào suy ra đáp án đúng khi đang làm bài.
     */
    private SubmissionDetail upsertAnswer(Integer examId, ExamSubmission session,
                                          SaveAnswerRequest request, LocalDateTime now) {
        ExamQuestion examQuestion = examQuestionRepository
                .findByExam_ExamIdAndQuestion_QuestionId(examId, request.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi id=" + request.getQuestionId()
                        + " không thuộc đề thi id=" + examId));

        ExamQuestionAnswer selected = resolveSelectedOption(examId, examQuestion, request);

        SubmissionDetail detail = detailRepository
                .findBySubmission_SubmissionIdAndQuestion_QuestionId(
                        session.getSubmissionId(), request.getQuestionId())
                .orElseGet(() -> SubmissionDetail.builder()
                        .submission(session)
                        .question(examQuestion.getQuestion())
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
            return null;    // bỏ chọn, hoặc câu tự luận
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
                // Chỉ ghi nhận "còn sống". ExpiresAt tuyệt đối không bị nới ra:
                // heartbeat dùng để phát hiện rớt mạng, không dùng để bù giờ.
                markAlive(session, now);
                session.setLastActiveAt(now);
                session.setAtRiskStatus(false);
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

    // ── Vấn đề 2: hết giờ là bài phải được nộp, dù client còn sống hay không ─

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

        // Lưới an toàn cho các câu autosave chưa kịp gửi lên. Quá deadline thì
        // không nhận thêm đáp án mới — chỉ chốt những gì đã lưu.
        if (!expired && request != null && request.getAnswers() != null) {
            for (SaveAnswerRequest answer : request.getAnswers()) {
                if (answer.getQuestionId() != null) {
                    upsertAnswer(examId, session, answer, now);
                }
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
        // Cả lô nằm trong một transaction: một bài lỗi thì lô đó không được chốt
        // và job chạy lần sau sẽ thử lại. Chấp nhận được vì lô rất nhỏ (chỉ gồm
        // các phiên vừa vượt deadline trong khoảng thời gian giữa hai lần quét).
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

        // Câu truy vấn trên chỉ lọc SƠ BỘ. Vì heartbeat không còn ghi
        // LastActiveAt xuống DB ở mọi nhịp (xem markAlive), cột đó luôn trễ tối
        // đa một chu kỳ flush, nên một em vẫn đang thi bình thường vẫn có thể
        // lọt vào danh sách này.
        //
        // Redis mới là nơi biết chính xác ai còn nhịp: key exam:alive:* hết hạn
        // đúng sau silenceSeconds im lặng. Hỏi cả lô trong một pipeline.
        Optional<Set<Integer>> alive = examRedis.findAlive(
                candidates.stream().map(ExamSubmission::getSubmissionId).toList());

        int flagged = 0;
        for (ExamSubmission session : candidates) {
            // Redis không trả lời -> tin cột LastActiveAt như thời chưa có Redis.
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

    /**
     * Ghi nhận học sinh còn sống, ưu tiên ghi vào Redis thay vì DB.
     *
     * Nhịp heartbeat đi qua đây mỗi 15-30 giây cho từng học sinh. Nếu mỗi nhịp
     * đều UPDATE cột LastActiveAt thì một phòng thi 500 em là hơn 30 UPDATE mỗi
     * giây vào đúng bảng đang chịu tải nặng nhất, chỉ để ghi một mốc thời gian.
     *
     * Nên: Redis nhận mọi nhịp (key exam:alive:* với TTL = ngưỡng im lặng), còn
     * DB chỉ được ghi khi {@code touchAlive} báo đã hết một chu kỳ flush — hoặc
     * khi Redis chết, lúc đó nó trả về true ở mọi nhịp và hành vi quay lại y như
     * cũ. Cột LastActiveAt vì thế vẫn dùng được, chỉ trễ tối đa một chu kỳ.
     */
    private void markAlive(ExamSubmission session, LocalDateTime now) {
        if (examRedis.touchAlive(session.getSubmissionId(), atRiskAfterSeconds)) {
            session.setLastActiveAt(now);
        }
        // Luôn hạ cờ: đây là dữ liệu giáo viên đang nhìn, không được để trễ.
        // Gán lại đúng giá trị cũ thì Hibernate không sinh UPDATE nào.
        session.setAtRiskStatus(false);
        List<ExamSubmission> silent = submissionRepository
                .findByStatusAndAtRiskStatusFalseAndLastActiveAtLessThan(
                        SubmissionStatus.IN_PROGRESS, threshold);
        silent.forEach(session -> session.setAtRiskStatus(true));
        if (!silent.isEmpty()) {
            log.info("Đánh dấu AtRiskStatus cho {} phiên im lặng quá {} giây",
                    silent.size(), silenceSeconds);
        }
        return silent.size();
    }

    /**
     * Chốt một phiên thi: chấm các câu trắc nghiệm theo snapshot đáp án của đề,
     * rồi đóng phiên lại.
     *
     * Dùng chung cho cả ba lối vào — học sinh bấm nộp, request bất kỳ phát hiện
     * đã quá giờ, và job quét định kỳ — nên không có đường nào để một phiên hết
     * giờ mà vẫn ở trạng thái IN_PROGRESS mãi.
     *
     * @param auto true nếu do server chốt hộ vì hết giờ
     */
    private ExamResultResponse finishSession(Exam exam, ExamSubmission session,
                                             LocalDateTime now, boolean auto) {
        List<ExamQuestion> examQuestions =
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId());
        Map<Integer, SubmissionDetail> details = detailsOf(session);

        BigDecimal total = BigDecimal.ZERO;
        boolean awaitingManual = false;

        for (ExamQuestion examQuestion : examQuestions) {
            Integer questionId = examQuestion.getId().getQuestionId();
            SubmissionDetail detail = details.get(questionId);
            if (detail == null) {
                // Câu bỏ trắng vẫn ghi một dòng 0 điểm để bảng kết quả đủ số câu.
                detail = SubmissionDetail.builder()
                        .submission(session)
                        .question(examQuestion.getQuestion())
                        .build();
                details.put(questionId, detail);
            }

            if (examQuestion.resolveType() == QuestionType.ESSAY) {
                // Tự luận không tự chấm được: để 0 điểm và chờ giáo viên / AI.
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
            }
            detailRepository.save(detail);
        }

        session.setTotalScore(total);
        session.setSubmittedAt(now);
        session.setAutoSubmitted(auto);
        session.setLastActiveAt(now);
        session.setAtRiskStatus(false);
        // Còn câu tự luận -> mới là SUBMITTED, điểm chưa phải điểm cuối.
        session.setStatus(awaitingManual ? SubmissionStatus.SUBMITTED : SubmissionStatus.GRADED);
        submissionRepository.save(session);

        // Bài đã chốt thì nhịp sống không còn ý nghĩa: dọn ngay để job quét
        // không phải hỏi Redis về những phiên đã đóng.
        examRedis.clearSession(session.getSubmissionId());

        log.info("Chốt bài submissionId={} examId={} auto={} score={} status={}",
                session.getSubmissionId(), exam.getExamId(), auto, total, session.getStatus());
        return buildResult(exam, session, examQuestions, details, true, true);
    }

    // ── Kết quả ─────────────────────────────────────────────────────────────

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
        return buildResult(exam, session,
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId()),
                detailsOf(session), true, true);
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
            // Danh sách lịch sử không kèm chi tiết từng câu và không lộ đáp án.
            history.add(buildResult(exam, session,
                    examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId()),
                    detailsOf(session), false, false));
        }
        return history;
    }

    /**
     * Gói trạng thái phiên thi cho client.
     *
     * Luôn kèm {@code expiresAt} + {@code serverTime} + {@code remainingSeconds}:
     * đây là cách client tính lại thời gian còn lại mà không cần tin đồng hồ máy
     * của học sinh.
     */
    private ExamSessionResponse toSessionResponse(Exam exam, ExamSubmission session, boolean resumed) {
        LocalDateTime now = LocalDateTime.now();
        // Phần đề (nội dung câu hỏi + lựa chọn) giống nhau với mọi học sinh nên
        // lấy từ cache Redis; phần đã làm là của riêng từng em nên luôn đọc DB.
        List<ExamQuestionView> questions = loadPaper(exam.getExamId());
        Map<Integer, SubmissionDetail> details = detailsOf(session);

        int answered = 0;

        for (ExamQuestionView question : questions) {
            SubmissionDetail detail = details.get(question.getQuestionId());
        List<ExamQuestion> examQuestions =
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId());
        Map<Integer, SubmissionDetail> details = detailsOf(session);

        List<ExamQuestionView> questions = new ArrayList<>();
        int answered = 0;

        for (ExamQuestion examQuestion : examQuestions) {
            Integer questionId = examQuestion.getId().getQuestionId();
            SubmissionDetail detail = details.get(questionId);
            Integer selectedId = selectedIdOf(detail);
            String essay = detail == null ? null : detail.getEssayResponse();
            if (selectedId != null || essay != null) {
                answered++;
            }
            // Ghép bài làm của em này vào bản đề vừa lấy. An toàn vì loadPaper
            // luôn trả về một bản dựng riêng cho request (giải mã lại từ Redis
            // hoặc dựng mới từ DB), không phải object dùng chung.
            question.setSelectedSnapshotAnswerId(selectedId);
            question.setEssayResponse(essay);
            question.setAnsweredAt(detail == null ? null : detail.getAnsweredAt());
            questions.add(ExamQuestionView.builder()
                    .questionId(questionId)
                    .questionOrder(examQuestion.getQuestionOrder())
                    .points(pointsOf(examQuestion))
                    .content(examQuestion.resolveContent())
                    .questionType(examQuestion.resolveType())
                    .options(optionsOf(exam.getExamId(), questionId, examQuestion.resolveType()))
                    .selectedSnapshotAnswerId(selectedId)
                    .essayResponse(essay)
                    .answeredAt(detail == null ? null : detail.getAnsweredAt())
                    .build());
        }

        return ExamSessionResponse.builder()
                .submissionId(session.getSubmissionId())
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .status(session.getStatus())
                .resumed(resumed)
                .startedAt(session.getStartedAt())
                .expiresAt(session.getExpiresAt())
                .serverTime(now)
                .remainingSeconds(session.remainingSeconds(now))
                .atRisk(Boolean.TRUE.equals(session.getAtRiskStatus()))
                .totalQuestions(questions.size())
                .answeredQuestions(answered)
                .questions(questions)
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
                    .correctAnswerContent(revealAnswers && !essay
                            ? correctAnswerOf(exam.getExamId(), questionId) : null)
                    .explanation(revealAnswers ? examQuestion.getExplanation() : null)
                    .awaitingManualGrading(awaiting)
                    .build());
        }

        return ExamResultResponse.builder()
                .submissionId(session.getSubmissionId())
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
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
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ học sinh mới được làm bài thi");
        }
        return user;
    }

    /** Đề gắn với lớp thì chỉ học sinh trong lớp đó được vào. */
    private void requireEnrolled(Exam exam, User student) {
        ClassEntity classEntity = exam.getClassEntity();
        if (classEntity == null) {
            return;     // đề luyện tập tự do, không thuộc lớp nào
        }
        if (!classStudentRepository.existsById_ClassIdAndId_StudentId(
                classEntity.getClassId(), student.getUserId())) {
            // Trả 404 chứ không 403: không tiết lộ đề tồn tại cho người ngoài lớp.
            throw new ResourceNotFoundException("Không tìm thấy đề thi id=" + exam.getExamId());
        }
    }

    private ExamSubmission requireSession(Integer examId, User student) {
        return submissionRepository.findByExam_ExamIdAndStudent_UserId(examId, student.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bạn chưa bắt đầu làm đề thi id=" + examId));
    }

    /**
     * Phiên phải đang mở mới cho ghi. Nếu đã quá deadline thì chốt bài ngay tại
     * đây rồi báo lỗi — mọi request của học sinh đều là một cơ hội để phát hiện
     * hết giờ, không phải chỉ trông vào job quét.
     */
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
            // Dữ liệu cũ tạo trước UNIQUE(SubmissionID, QuestionID) có thể trùng
            // câu hỏi; giữ dòng sau cùng.
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

    /**
     * Bản đề để học sinh làm bài: câu hỏi theo thứ tự, kèm các lựa chọn.
     *
     * Đây là phần đắt nhất của mỗi lần vào phòng thi — một query lấy câu hỏi
     * cộng thêm một query lấy lựa chọn cho TỪNG câu, tức đề 40 câu là 41 query,
     * và cả lớp vào cùng lúc thì nhân lên bằng sĩ số. Nội dung lại hoàn toàn
     * giống nhau giữa các em (đề đã snapshot, không đổi giữa chừng), nên đây
     * đúng là thứ để trong Redis.
     *
     * Trả về một bản dựng riêng cho mỗi request — hoặc giải mã lại từ JSON trong
     * Redis, hoặc dựng mới từ DB — nên phía gọi được phép ghi bài làm của học
     * sinh vào đó mà không đụng tới ai khác.
     *
     * Cache miss hay Redis chết đều đi tiếp bằng đường DB, học sinh không thấy
     * khác gì ngoài việc chậm hơn một chút.
     */
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
                    .build());
        }
        // Ghi cache TRƯỚC khi phía gọi ghép bài làm vào: cái được cất đi phải là
        // bản đề trắng, không dính đáp án của em vừa gọi.
        examRedis.putPaper(examId, paper);
        return paper;
    }

    /** Các lựa chọn hiển thị cho học sinh. Không bao giờ kèm cờ đáp án đúng. */
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

    /** Đáp án đúng theo snapshot của đề. Chỉ dùng cho trang kết quả. */
    private String correctAnswerOf(Integer examId, Integer questionId) {
        return snapshotAnswerRepository
                .findByExamQuestion_Exam_ExamIdAndExamQuestion_Question_QuestionIdOrderByAnswerOrderAsc(
                        examId, questionId)
                .stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getIsCorrect()))
                .map(ExamQuestionAnswer::getAnswerContent)
                .findFirst()
                .orElse(null);
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
