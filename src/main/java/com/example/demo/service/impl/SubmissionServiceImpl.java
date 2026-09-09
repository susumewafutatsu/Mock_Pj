package com.example.demo.service.impl;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.SubmissionStatus;

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
import com.example.demo.repository.RoomExamRepository;
import com.example.demo.repository.ExamQuestionAnswerRepository;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
 * bị hoàn tác, thí sinh gọi lại lần nữa lại rơi vào đúng nhánh đó — nên các
 * method này khai báo không rollback với BusinessException. Mọi BusinessException
 * trong lớp này đều được ném ở vị trí không có thay đổi nào cần huỷ.
 *
 * Ghi chú về Redis ({@link ExamRedisService}): MySQL vẫn là nguồn sự thật duy
 * nhất của phiên thi. Redis chỉ gánh ba việc mà DB làm thì tốn kém:
 *
 *   - Cache đề thi đã snapshot, để mỗi lần vào phòng thi không phải dựng lại
 *     danh sách câu hỏi + lựa chọn bằng hàng chục query.
 *   - Khoá hẹp theo (đề, thí sinh) lúc tạo phiên, thay cho khoá dòng đề thi vốn
 *     bắt cả lớp xếp hàng đúng lúc vào thi.
 *   - Nhịp sống (presence) của thí sinh, để heartbeat 15 giây một lần không biến
 *     thành một UPDATE xuống DB mỗi lần.
 *
 * Mất Redis không làm hỏng bài thi: mọi lối gọi đều có đường lui về MySQL, đúng
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
    private final RoomExamRepository roomExamRepository;
    private final UserRepository userRepository;
    private final ExamRedisService examRedis;
    private final MistakeBookService mistakeBookService;

    /**
     * Ngưỡng im lặng coi là mất kết nối — cũng chính là TTL của key nhịp sống
     * trong Redis. Dùng chung một giá trị với {@code ExamSessionScheduler} để
     * key hết hạn đúng lúc job đi quét, không lệch nhau.
     */
    @Value("${exam.session.at-risk-after-seconds:90}")
    private long atRiskAfterSeconds;

    // ── Vấn đề 1: một lượt làm — một phiên ──────────────────────────────────

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public ExamSessionResponse startOrResume(Integer examId, String studentEmail) {
        User student = requireStudent(studentEmail);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        requireCanTakeExam(exam, student);

        // Nhánh nhanh: lượt gần nhất còn dở thì đây là "vào lại phòng thi",
        // không khoá gì. Lượt gần nhất đã nộp thì rơi xuống nhánh mở lượt mới.
        Optional<ExamSubmission> latest = submissionRepository
                .findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(
                        examId, student.getUserId());
        if (latest.isPresent() && latest.get().isInProgress()) {
            return resumeExisting(exam, latest.get());
        }

        requireExamWindowOpen(exam);
        // Kiểm sớm để thí sinh hết lượt không phải chờ giành khoá; kiểm lại lần
        // nữa sau khi có khoá vì con số này có thể đổi giữa hai request song song.
        requireAttemptAvailable(exam, student);

        // Nhánh tạo mới cần chống hai request song song của cùng một em (double
        // click, hai tab) cùng thấy "chưa có phiên" rồi cùng insert.
        //
        // Khoá Redis hẹp theo (đề, thí sinh) nên cả lớp bấm "Bắt đầu" cùng lúc
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
                .findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(
                        examId, student.getUserId());
        if (afterLock.isPresent() && afterLock.get().isInProgress()) {
            return resumeExisting(exam, afterLock.get());
        }

        if (examQuestionRepository.countByExam_ExamId(examId) == 0) {
            throw new BusinessException("Đề thi chưa có câu hỏi nào, chưa thể bắt đầu");
        }

        // Đếm lại trong khoá: đây mới là con số quyết định. Số lượt đã dùng cũng
        // chính là số thứ tự của lượt sắp mở (đã dùng 2 -> đang mở lượt 3).
        long used = requireAttemptAvailable(exam, student);

        LocalDateTime now = LocalDateTime.now();
        ExamSubmission session = ExamSubmission.builder()
                .exam(exam)
                .student(student)
                .attemptNumber((int) used + 1)
                .startedAt(now)
                .expiresAt(computeExpiry(exam, now))
                .lastActiveAt(now)
                .status(SubmissionStatus.IN_PROGRESS)
                .build();
        submissionRepository.save(session);

        // Mở nhịp sống ngay từ lúc vào phòng, đừng đợi heartbeat đầu tiên: nếu
        // không, phiên vừa tạo đã bị job quét coi là mất kết nối.
        examRedis.touchAlive(session.getSubmissionId(), atRiskAfterSeconds);

        log.info("Bắt đầu phiên thi submissionId={} examId={} studentId={} lượt={}/{} expiresAt={}",
                session.getSubmissionId(), examId, student.getUserId(), session.getAttemptNumber(),
                exam.isUnlimitedAttempts() ? "∞" : exam.getMaxAttempts(), session.getExpiresAt());
        return toSessionResponse(exam, session, false);
    }

    /**
     * Trả khoá tạo phiên, nhưng chỉ SAU khi transaction kết thúc.
     *
     * Trả ngay trong thân method là sai: lúc đó dòng ExamSubmissions vừa insert
     * chưa commit, nên request thứ hai giành được khoá sẽ KHÔNG thấy nó và
     * insert thêm một phiên nữa. Điều này đúng với mọi mức cô lập trừ READ
     * UNCOMMITTED — dự án không cấu hình đè nên đang chạy mặc định của InnoDB
     * là REPEATABLE READ. Đợi tới afterCompletion thì request sau chắc chắn đọc
     * được phiên vừa tạo và đi nhánh "vào lại phòng".
     *
     * Trả cả khi transaction rollback — khi đó không có phiên nào được tạo nên
     * thí sinh phải được thử lại ngay, không phải chờ hết TTL.
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
            // Chỉ tới được đây qua GET /session — luồng start đã lọc trước và đi
            // nhánh mở lượt mới. Ở đây là "đọc lại phiên đang dở" mà phiên đó
            // vừa bị chốt (hết giờ, hoặc nộp từ một tab khác).
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

    /**
     * Thí sinh còn lượt làm bài trên đề này không.
     *
     * @return số lượt đã dùng — người gọi dùng luôn con số này làm số thứ tự
     *         cho lượt sắp mở, khỏi đếm lại lần nữa
     * @throws BusinessException nếu đã dùng hết số lượt người ra đề cho phép
     */
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
     * Đây là chỗ giải quyết chuyện mất tiến độ: thí sinh vừa bấm chọn là dữ liệu
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
     * Ghi nhận thí sinh còn sống, ưu tiên ghi vào Redis thay vì DB.
     *
     * Nhịp heartbeat đi qua đây mỗi 15-30 giây cho từng thí sinh. Nếu mỗi nhịp
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
        // Luôn hạ cờ: đây là dữ liệu người ra đề đang nhìn, không được để trễ.
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
     * Dùng chung cho cả ba lối vào — thí sinh bấm nộp, request bất kỳ phát hiện
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
        // Câu làm sai được gom lại để đẩy sang sổ tay câu sai sau khi chấm xong.
        // Chỉ gom câu trắc nghiệm: câu tự luận đang mang IsCorrect = false vì
        // CHƯA ĐƯỢC CHẤM, không phải vì thí sinh làm sai — đưa nó vào sổ tay là
        // bắt người ta ôn lại một câu mà chính hệ thống còn chưa biết đúng hay sai.
        List<Integer> wrongQuestionIds = new ArrayList<>();

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
                // Tự luận không tự chấm được: để 0 điểm và chờ người ra đề / AI.
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
        // Còn câu tự luận -> mới là SUBMITTED, điểm chưa phải điểm cuối.
        session.setStatus(awaitingManual ? SubmissionStatus.SUBMITTED : SubmissionStatus.GRADED);
        submissionRepository.save(session);

        // Bài đã chốt thì nhịp sống không còn ý nghĩa: dọn ngay để job quét
        // không phải hỏi Redis về những phiên đã đóng.
        examRedis.clearSession(session.getSubmissionId());

        // Đẩy câu sai sang sổ tay ôn tập. Bọc try/catch vì việc này là hệ quả
        // của việc nộp bài chứ không phải một phần của nó: sổ tay lỗi thì bài
        // vẫn phải được chốt và thí sinh vẫn phải thấy điểm. Đây đúng là kiểu
        // ngoại lệ hiếm hoi đáng nuốt — mất một mục trong sổ tay là phiền,
        // mất một bài thi là hỏng.
        try {
            mistakeBookService.recordMistakes(session.getStudent(), wrongQuestionIds, now);
        } catch (Exception e) {
            log.error("Không ghi được sổ tay câu sai cho submissionId={}",
                    session.getSubmissionId(), e);
        }

        log.info("Chốt bài submissionId={} examId={} auto={} score={} status={}",
                session.getSubmissionId(), exam.getExamId(), auto, total, session.getStatus());
        // Màn hình ngay sau khi nộp cũng tuân theo cờ AllowReview của đề — nếu
        // không, tắt cờ chỉ chặn được trang xem lại, còn màn hình nộp bài vẫn lộ
        // hết đáp án cho lượt sau.
        return buildResult(exam, session, examQuestions, details,
                Boolean.TRUE.equals(exam.getAllowReview()), true);
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
        // Đáp án đúng + giải thích chỉ mở khi người ra đề cho phép. Với đề ôn tập
        // thì đây chính là phần thí sinh học được, nên mặc định của cột là bật;
        // đề kiểm tra thì người ra đề tắt để lượt sau không thành chép đáp án.
        boolean reveal = Boolean.TRUE.equals(exam.getAllowReview());
        ExamResultResponse result = buildResult(exam, session,
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId()),
                detailsOf(session), reveal, true);

        // Trang xem lại bài là chỗ thí sinh quyết định có làm lại hay không, nên
        // trả luôn tình trạng lượt — khỏi phải gọi thêm danh sách đề.
        long used = submissionRepository.countByExam_ExamIdAndStudent_UserId(
                exam.getExamId(), student.getUserId());
        result.setAttemptsUsed(used);
        result.setCanRetake(exam.allowsAttempt(used));
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
        // Bài mới nộp lên đầu. Một đề giờ có thể có nhiều lượt, nên trong cùng
        // một đề còn phải sắp theo lượt giảm dần để "Lần 3" không nằm dưới "Lần 1"
        // khi hai lượt nộp cùng lúc (dữ liệu cũ có thể thiếu SubmittedAt).
        history.sort(Comparator
                .comparing(ExamResultResponse::getSubmittedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ExamResultResponse::getAttemptNumber,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return history;
    }

    /**
     * Gói trạng thái phiên thi cho client.
     *
     * Luôn kèm {@code expiresAt} + {@code serverTime} + {@code remainingSeconds}:
     * đây là cách client tính lại thời gian còn lại mà không cần tin đồng hồ máy
     * của thí sinh.
     */
    private ExamSessionResponse toSessionResponse(Exam exam, ExamSubmission session, boolean resumed) {
        LocalDateTime now = LocalDateTime.now();
        // Phần đề (nội dung câu hỏi + lựa chọn) giống nhau với mọi thí sinh nên
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
                    // Các lựa chọn hiện cả khi đề tắt xem đáp án: thí sinh vẫn
                    // được nhìn lại bài của chính mình, chỉ không biết cái nào
                    // đúng — đúng bằng thứ em thấy lúc đang làm bài.
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
                // Cờ của ĐỀ, không phải của payload này: danh sách lịch sử luôn
                // gọi với revealAnswers=false cho nhẹ, nhưng vẫn phải nói đúng
                // rằng bấm vào xem chi tiết thì có đáp án hay không.
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
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ thí sinh mới được làm bài thi");
        }
        return user;
    }

    /**
     * Thí sinh có được làm đề này không.
     *
     * Thay cho {@code requireEnrolled} thời còn lớp học. Luật mới có hai nhánh:
     *
     *   - Đề công khai: ai cũng làm được, không cần thuộc về đâu cả. Đây chính
     *     là thứ cho phép "thí sinh tự do thi" mà mô hình lớp không làm được.
     *   - Đề gắn phòng: phải là thành viên đang hoạt động của một phòng có chứa
     *     đề đó, và phòng phải còn ở trạng thái cho làm bài.
     *
     * Ba điều kiện của nhánh sau được kiểm trong MỘT câu truy vấn
     * ({@code canUserTakeExam}) chứ không tách rời: kiểm thiếu vế trạng thái
     * phòng thì người bị mời ra vẫn thi được, thiếu vế ACTIVE thì người đã rời
     * phòng cũng vậy.
     */
    private void requireCanTakeExam(Exam exam, User student) {
        if (Boolean.TRUE.equals(exam.getIsPublic())) {
            return;
        }
        if (!roomExamRepository.canUserTakeExam(exam.getExamId(), student.getUserId())) {
            // Trả 404 chứ không 403: không tiết lộ đề tồn tại cho người ngoài phòng.
            throw new ResourceNotFoundException("Không tìm thấy đề thi id=" + exam.getExamId());
        }
    }

    /**
     * Phiên mà mọi thao tác trong phòng thi tác động lên: LƯỢT GẦN NHẤT.
     *
     * Cố tình không lọc riêng IN_PROGRESS. Lượt gần nhất vừa bị chốt (hết giờ,
     * hoặc nộp từ tab khác) vẫn phải trả về được, để heartbeat và submit báo cho
     * client biết "bài đã nộp rồi" thay vì 404 — đúng hành vi từ trước khi có
     * nhiều lượt. Việc phiên còn mở hay không do {@link #requireActiveSession}
     * và các nhánh {@code isInProgress()} ở dưới quyết định.
     */
    private ExamSubmission requireSession(Integer examId, User student) {
        return submissionRepository
                .findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(
                        examId, student.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bạn chưa bắt đầu làm đề thi id=" + examId));
    }

    /**
     * Phiên phải đang mở mới cho ghi. Nếu đã quá deadline thì chốt bài ngay tại
     * đây rồi báo lỗi — mọi request của thí sinh đều là một cơ hội để phát hiện
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
     * Bản đề để thí sinh làm bài: câu hỏi theo thứ tự, kèm các lựa chọn.
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
     * Cache miss hay Redis chết đều đi tiếp bằng đường DB, thí sinh không thấy
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
