package com.example.demo.service.impl;

import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.ClassEntity;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.ClassExamGroup;
import com.example.demo.dto.response.ExamResponse;
import com.example.demo.dto.response.PracticeExamsResponse;
import com.example.demo.dto.response.PracticeLevelOption;
import com.example.demo.dto.response.StudentExamBoardResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cài đặt phần "chọn đề để làm" của học sinh.
 *
 * Ba lối vào (trang chủ, đề của một lớp, đề luyện tập) dùng chung một bộ hàm
 * dựng {@link ExamResponse}, nên trạng thái của một đề luôn được tính giống
 * nhau bất kể học sinh mở nó từ màn hình nào.
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    /** Số đề luyện tập hiện ở trang chủ. Xem đầy đủ thì sang trang riêng. */
    private static final int PRACTICE_PREVIEW_SIZE = 6;

    /**
     * Khoảng cho phép của tham số size.
     *
     * Kẹp lại chứ không tin số client gửi lên: một request {@code ?size=100000}
     * sẽ kéo cả bảng Exams về rồi dựng ngần ấy DTO. Không cần ác ý — chỉ một lỗi
     * vòng lặp ở front-end là đủ.
     */
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    // ── Trang chủ ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StudentExamBoardResponse getExamBoard(String studentEmail) {
        User student = requireStudent(studentEmail);
        LocalDateTime now = LocalDateTime.now();

        List<Integer> classIds = classStudentRepository.findClassIdsByStudentId(student.getUserId());
        List<ClassEntity> classes = classIds.isEmpty()
                ? List.of()
                : classRepository.findAllByIdWithDetails(classIds);
        List<Exam> classExams = classIds.isEmpty()
                ? List.of()
                : examRepository.findByClassIdIn(classIds);

        // Trình độ của các lớp đang học — cũng chính là bộ lọc mặc định cho
        // phần luyện tập bên dưới.
        //
        // Em chưa vào lớp nào thì không có trình độ nào để lọc. Lúc đó KHÔNG kéo
        // toàn bộ đề tự do trong hệ thống về chỉ để hiện sáu dòng xem trước —
        // lấy đúng sáu đề mới nhất. Đổi lại là phần xem trước của trường hợp này
        // sắp theo "mới nhất" chứ không theo mức độ gấp; chấp nhận được vì em
        // chưa có lớp thì cũng chưa có bài nào được giao.
        Set<Integer> enrolledLevelIds = levelIdsOf(classes);
        List<Exam> practiceExams = enrolledLevelIds.isEmpty()
                ? examRepository.findPracticeExams(null, null,
                        PageRequest.of(0, PRACTICE_PREVIEW_SIZE,
                                Sort.by(Sort.Direction.DESC, "examId"))).getContent()
                : examRepository.findPracticeExamsByLevelIdIn(enrolledLevelIds);

        // Một lượt đếm câu hỏi và một lượt đọc bài làm cho CẢ hai nhóm, thay vì
        // mỗi nhóm một lượt.
        List<Exam> allExams = new ArrayList<>(classExams.size() + practiceExams.size());
        allExams.addAll(classExams);
        allExams.addAll(practiceExams);
        Context ctx = contextOf(student, allExams, now);

        // Gom đề theo lớp. Lớp chưa có đề nào vẫn xuất hiện với danh sách rỗng —
        // học sinh cần thấy lớp mình ở đó, không phải thấy nó biến mất.
        Map<Integer, List<ExamResponse>> byClass = new HashMap<>();
        for (Exam exam : classExams) {
            byClass.computeIfAbsent(exam.getClassEntity().getClassId(), k -> new ArrayList<>())
                    .add(toResponse(exam, ExamResponse.Source.CLASS, ctx));
        }

        List<ClassExamGroup> groups = new ArrayList<>(classes.size());
        int pendingTotal = 0;
        for (ClassEntity cls : classes) {
            List<ExamResponse> rows = byClass.getOrDefault(cls.getClassId(), new ArrayList<>());
            rows.sort(URGENCY);
            int pending = countPending(rows);
            pendingTotal += pending;
            groups.add(toGroup(cls, rows, pending));
        }
        groups.sort(Comparator.comparing(ClassExamGroup::getPendingCount).reversed()
                .thenComparing(ClassExamGroup::getClassName, Comparator.nullsLast(String::compareTo)));

        List<ExamResponse> practiceRows = toResponses(practiceExams, ExamResponse.Source.PRACTICE, ctx);
        practiceRows.sort(URGENCY);
        boolean truncated = practiceRows.size() > PRACTICE_PREVIEW_SIZE;
        if (truncated) {
            practiceRows = new ArrayList<>(practiceRows.subList(0, PRACTICE_PREVIEW_SIZE));
        }

        return StudentExamBoardResponse.builder()
                .classes(groups)
                .practice(practiceRows)
                .pendingCount(pendingTotal)
                .practiceTruncated(truncated)
                .serverTime(now)
                .build();
    }

    // ── Đề của một lớp ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ExamResponse> getClassExams(Integer classId, String studentEmail) {
        User student = requireStudent(studentEmail);

        // 404 chứ không 403: người ngoài lớp không được biết lớp đó có tồn tại.
        // Cùng một luật với requireEnrolled lúc bắt đầu thi, nên không có lớp
        // nào nhìn thấy được ở đây mà bấm vào lại bị chặn ở bước sau.
        if (!classStudentRepository.existsById_ClassIdAndId_StudentId(classId, student.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Exam> exams = examRepository.findByClassIdIn(List.of(classId));
        List<ExamResponse> rows = toResponses(
                exams, ExamResponse.Source.CLASS, contextOf(student, exams, now));
        rows.sort(URGENCY);
        return rows;
    }

    // ── Đề luyện tập tự do ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PracticeExamsResponse getPracticeExams(Integer levelId, Integer subjectId,
                                                  boolean allLevels, int page, int size,
                                                  String studentEmail) {
        User student = requireStudent(studentEmail);
        LocalDateTime now = LocalDateTime.now();

        Set<Integer> enrolledLevelIds = enrolledLevelIdsOf(student);
        List<PracticeLevelOption> levelOptions = practiceLevelOptions(enrolledLevelIds);

        // Học sinh chưa chọn gì thì server chọn hộ một trình độ để mở màn, thay
        // vì đổ ra toàn bộ đề của mọi trình độ. Chọn theo lớp em đang học và
        // phải là trình độ THỰC SỰ có đề, nếu không màn hình đầu tiên em thấy
        // lại là một danh sách rỗng.
        Integer effectiveLevelId = levelId;
        boolean defaulted = false;
        if (levelId == null && subjectId == null && !allLevels) {
            Integer suggestion = firstEnrolledLevelWithExams(levelOptions);
            if (suggestion != null) {
                effectiveLevelId = suggestion;
                defaulted = true;
            }
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);

        // Sắp theo đề mới nhất trước, ngay trong SQL.
        //
        // Cố tình KHÔNG dùng thứ tự theo mức độ gấp như hai màn hình kia: mức đó
        // tính từ bài làm của từng học sinh nên chỉ có trong bộ nhớ, mà sắp trong
        // bộ nhớ khi đã phân trang thì chỉ sắp được đúng trang hiện tại — nhìn
        // thì có thứ tự nhưng thực chất là sai. Đề luyện tập cũng không có hạn
        // nộp nên "gấp" không phải là khái niệm áp dụng được ở đây; việc còn dở
        // dang đã được nhắc ở trang chủ.
        Page<Exam> examPage = examRepository.findPracticeExams(
                effectiveLevelId, subjectId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "examId")));

        List<Exam> exams = examPage.getContent();
        List<ExamResponse> rows = toResponses(
                exams, ExamResponse.Source.PRACTICE, contextOf(student, exams, now));

        return PracticeExamsResponse.builder()
                .levels(levelOptions)
                .appliedLevelId(effectiveLevelId)
                .appliedSubjectId(subjectId)
                .filteredByEnrolledLevels(defaulted)
                .exams(rows)
                .page(examPage.getNumber())
                .size(safeSize)
                .totalElements(examPage.getTotalElements())
                .totalPages(examPage.getTotalPages())
                .serverTime(now)
                .build();
    }

    /** Trình độ đầu tiên học sinh đang học mà có ít nhất một đề luyện tập. */
    private Integer firstEnrolledLevelWithExams(List<PracticeLevelOption> options) {
        for (PracticeLevelOption option : options) {
            if (option.isEnrolled() && option.getExamCount() > 0) {
                return option.getLevelId();
            }
        }
        return null;
    }

    /**
     * Bộ lọc của trang luyện tập: chỉ những trình độ thực sự có đề, kèm số
     * lượng, và đánh dấu trình độ học sinh đang học.
     */
    private List<PracticeLevelOption> practiceLevelOptions(Set<Integer> enrolledLevelIds) {
        List<PracticeLevelOption> options = new ArrayList<>();
        for (ExamRepository.PracticeLevelCount row : examRepository.countPracticeExamsByLevel()) {
            options.add(PracticeLevelOption.builder()
                    .levelId(row.getLevelId())
                    .levelName(row.getLevelName())
                    .subjectId(row.getSubjectId())
                    .subjectName(row.getSubjectName())
                    .examCount(row.getTotal())
                    .enrolled(enrolledLevelIds.contains(row.getLevelId()))
                    .build());
        }
        return options;
    }

    // ── Dựng response ───────────────────────────────────────────────────────

    /**
     * Dữ liệu dùng chung cho cả lô đề: số câu hỏi của từng đề và bài làm của
     * học sinh, mỗi thứ đọc đúng một lần.
     *
     * Gom lại thành một object thay vì truyền hai Map rời qua từng hàm, để chỗ
     * gọi không thể lỡ tay truyền nhầm thứ tự hai tham số cùng kiểu.
     */
    private record Context(Map<Integer, Long> questionCounts,
                           Map<Integer, ExamSubmission> submissions,
                           LocalDateTime now) {
    }

    private Context contextOf(User student, List<Exam> exams, LocalDateTime now) {
        return new Context(questionCountsOf(exams), submissionsOf(student), now);
    }

    private List<ExamResponse> toResponses(List<Exam> exams, ExamResponse.Source source,
                                           Context ctx) {
        List<ExamResponse> rows = new ArrayList<>(exams.size());
        for (Exam exam : exams) {
            rows.add(toResponse(exam, source, ctx));
        }
        return rows;
    }

    private ExamResponse toResponse(Exam exam, ExamResponse.Source source, Context ctx) {
        ClassEntity classEntity = exam.getClassEntity();
        SubjectLevel level = exam.getLevel();
        ExamSubmission submission = ctx.submissions().get(exam.getExamId());
        int totalQuestions = ctx.questionCounts()
                .getOrDefault(exam.getExamId(), 0L).intValue();

        ExamResponse.Availability availability =
                resolveAvailability(exam, submission, totalQuestions, ctx.now());
        boolean inProgress = availability == ExamResponse.Availability.IN_PROGRESS;

        return ExamResponse.builder()
                .examId(exam.getExamId())
                .title(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .adaptive(Boolean.TRUE.equals(exam.getIsAdaptive()))
                .totalQuestions(totalQuestions)
                .source(source)
                .classId(classEntity == null ? null : classEntity.getClassId())
                .className(classEntity == null ? null : classEntity.getClassName())
                .levelId(level == null ? null : level.getLevelId())
                .levelName(level == null ? null : level.getLevelName())
                .subjectId(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectId())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .teacherName(exam.getCreatedBy() == null ? null : exam.getCreatedBy().getFullName())
                .availability(availability)
                .submissionId(submission == null ? null : submission.getSubmissionId())
                .submissionStatus(submission == null ? null : submission.getStatus())
                // Chỉ phiên còn đang chạy mới có deadline đáng để client đếm ngược.
                .expiresAt(inProgress ? submission.getExpiresAt() : null)
                .remainingSeconds(inProgress ? submission.remainingSeconds(ctx.now()) : 0L)
                .totalScore(submission == null || submission.isInProgress()
                        ? null : submission.getTotalScore())
                .submittedAt(submission == null ? null : submission.getSubmittedAt())
                .serverTime(ctx.now())
                .build();
    }

    private ClassExamGroup toGroup(ClassEntity cls, List<ExamResponse> exams, int pending) {
        SubjectLevel level = cls.getLevel();
        return ClassExamGroup.builder()
                .classId(cls.getClassId())
                .className(cls.getClassName())
                .courseCode(cls.getCourseCode())
                .teacherName(cls.getTeacher() == null ? null : cls.getTeacher().getFullName())
                .levelId(level == null ? null : level.getLevelId())
                .levelName(level == null ? null : level.getLevelName())
                .subjectId(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectId())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .pendingCount(pending)
                .exams(exams)
                .build();
    }

    private ExamResponse.Availability resolveAvailability(Exam exam, ExamSubmission submission,
                                                          int totalQuestions, LocalDateTime now) {
        if (submission != null) {
            if (!submission.isInProgress()) {
                return ExamResponse.Availability.SUBMITTED;
            }
            return submission.isExpiredAt(now)
                    ? ExamResponse.Availability.SUBMITTED
                    : ExamResponse.Availability.IN_PROGRESS;
        }
        if (totalQuestions == 0) {
            return ExamResponse.Availability.NO_QUESTIONS;
        }
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            return ExamResponse.Availability.UPCOMING;
        }
        if (exam.getEndTime() != null && !now.isBefore(exam.getEndTime())) {
            return ExamResponse.Availability.CLOSED;
        }
        return ExamResponse.Availability.OPEN;
    }

    // ── Thứ tự hiển thị ─────────────────────────────────────────────────────

    /**
     * Mức ưu tiên hiển thị của từng trạng thái. Số nhỏ đứng trước.
     *
     * Đây là chỗ thay cho {@code order by startTime desc} cũ. Sắp theo thời gian
     * tạo trả lời câu hỏi "đề nào mới nhất", nhưng người đang mở màn hình này
     * hỏi "tôi phải làm gì bây giờ" — và câu trả lời đó là bài đang làm dở
     * trước, rồi tới bài đang mở, rồi mới tới thứ chưa tới hạn.
     */
    private static int urgencyRank(ExamResponse.Availability availability) {
        return switch (availability) {
            case IN_PROGRESS -> 0;      // đang làm dở, đồng hồ đang chạy
            case OPEN -> 1;             // vào được ngay
            case UPCOMING -> 2;         // sắp tới
            case NO_QUESTIONS -> 3;     // giáo viên chưa gắn câu hỏi
            case SUBMITTED -> 4;        // xong rồi, chỉ để xem lại điểm
            case CLOSED -> 5;           // hết hạn mà không làm
        };
    }

    /**
     * Trong cùng một mức ưu tiên thì đề nào gấp hơn đứng trước: sắp đóng trước
     * (endTime tăng dần), đề không có hạn thì xuống cuối nhóm.
     */
    private static final Comparator<ExamResponse> URGENCY =
            Comparator.<ExamResponse>comparingInt(r -> urgencyRank(r.getAvailability()))
                    .thenComparing(ExamResponse::getEndTime,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ExamResponse::getExamId,
                            Comparator.nullsLast(Comparator.reverseOrder()));

    /** Số đề học sinh còn phải làm: đang dở, đang mở, hoặc sắp mở. */
    private int countPending(List<ExamResponse> rows) {
        int pending = 0;
        for (ExamResponse row : rows) {
            if (row.getAvailability() == ExamResponse.Availability.IN_PROGRESS
                    || row.getAvailability() == ExamResponse.Availability.OPEN
                    || row.getAvailability() == ExamResponse.Availability.UPCOMING) {
                pending++;
            }
        }
        return pending;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Set<Integer> enrolledLevelIdsOf(User student) {
        List<Integer> classIds = classStudentRepository.findClassIdsByStudentId(student.getUserId());
        if (classIds.isEmpty()) {
            return Set.of();
        }
        return levelIdsOf(classRepository.findAllByIdWithDetails(classIds));
    }

    /** LinkedHashSet để thứ tự trình độ ổn định giữa các lần gọi. */
    private Set<Integer> levelIdsOf(List<ClassEntity> classes) {
        Set<Integer> levelIds = new LinkedHashSet<>();
        for (ClassEntity cls : classes) {
            if (cls.getLevel() != null) {
                levelIds.add(cls.getLevel().getLevelId());
            }
        }
        return levelIds;
    }

    private Map<Integer, Long> questionCountsOf(List<Exam> exams) {
        Map<Integer, Long> counts = new HashMap<>();
        if (exams.isEmpty()) {
            // countByExamIdIn với danh sách rỗng sinh ra `in ()` — SQL không hợp
            // lệ trên một số DB, nên chặn ngay ở đây.
            return counts;
        }
        List<Integer> examIds = exams.stream().map(Exam::getExamId).toList();
        for (ExamQuestionRepository.ExamQuestionCount row
                : examQuestionRepository.countByExamIdIn(examIds)) {
            counts.put(row.getExamId(), row.getTotal());
        }
        return counts;
    }

    /** Phiên làm bài của học sinh, tra theo ExamID. Tối đa một phiên mỗi đề. */
    private Map<Integer, ExamSubmission> submissionsOf(User student) {
        Map<Integer, ExamSubmission> map = new HashMap<>();
        for (ExamSubmission submission : submissionRepository.findByStudentUserId(student.getUserId())) {
            map.put(submission.getExam().getExamId(), submission);
        }
        return map;
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ học sinh mới xem được danh sách đề thi của mình");
        }
        return user;
    }
}
