package com.example.demo.service.impl;

import com.example.demo.domain.enums.CourseStatus;
import com.example.demo.domain.enums.LessonType;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.Course;
import com.example.demo.domain.model.CourseEnrollment;
import com.example.demo.domain.model.CourseEnrollmentKey;
import com.example.demo.domain.model.CourseLesson;
import com.example.demo.domain.model.Deck;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.LessonCompletion;
import com.example.demo.domain.model.LessonCompletionKey;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.CourseCreateRequest;
import com.example.demo.dto.request.CourseReviewRequest;
import com.example.demo.dto.request.LessonCreateRequest;
import com.example.demo.dto.response.CourseDetailResponse;
import com.example.demo.dto.response.CourseResponse;
import com.example.demo.dto.response.LessonDetailResponse;
import com.example.demo.dto.response.LessonSummaryResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.CourseEnrollmentRepository;
import com.example.demo.repository.CourseLessonRepository;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.DeckRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.LessonCompletionRepository;
import com.example.demo.repository.SubjectLevelRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CourseService;
import com.example.demo.util.DbTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cài đặt LỘ TRÌNH ÔN TẬP (trong code vẫn tên Course / CourseLesson — đổi tên bảng và lớp là một đợt chuyển dữ liệu không mang lại gì cho người dùng). */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    private final CourseRepository courseRepository;

    private final com.example.demo.service.NotificationService notificationService;
    private final CourseLessonRepository lessonRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final LessonCompletionRepository completionRepository;
    private final SubjectLevelRepository levelRepository;
    private final DeckRepository deckRepository;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final com.example.demo.repository.ExamSubmissionRepository submissionRepository;
    private final com.example.demo.repository.ExamQuestionRepository examQuestionRepository;

    // ── Người ra đề ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getMyCourses(String authorEmail) {
        User author = requireUser(authorEmail);
        return describeAll(courseRepository.findByAuthor(author.getUserId()), author);
    }

    @Override
    @Transactional
    public CourseResponse createCourse(String authorEmail, CourseCreateRequest request) {
        User author = requireAuthor(authorEmail);
        Course course = Course.builder()
                .title(request.getTitle().trim())
                .description(trimToNull(request.getDescription()))
                .level(resolveLevel(request.getLevelId()))
                .author(author)
                // Luôn bắt đầu ở DRAFT. Client không gửi được trạng thái lên —
                // để nó tự đặt là mở đường cho người ra đề tự xuất bản khoá.
                .status(CourseStatus.DRAFT)
                .build();
        courseRepository.save(course);
        log.info("Tạo khoá học courseId={} author={}", course.getCourseId(), author.getUserId());
        return describe(course, author, 0L, 0L, false);
    }

    @Override
    @Transactional
    public CourseResponse updateCourse(String authorEmail, Integer courseId,
                                       CourseCreateRequest request) {
        User author = requireUser(authorEmail);
        Course course = requireAuthoredCourse(courseId, author);
        requireEditable(course);

        course.setTitle(request.getTitle().trim());
        course.setDescription(trimToNull(request.getDescription()));
        course.setLevel(resolveLevel(request.getLevelId()));
        course.markContentChanged();
        courseRepository.save(course);
        return describe(course, author);
    }

    @Override
    @Transactional
    public void deleteCourse(String authorEmail, Integer courseId) {
        User author = requireUser(authorEmail);
        Course course = requireAuthoredCourse(courseId, author);

        if (enrollmentRepository.countById_CourseId(courseId) > 0) {
            throw new BusinessException(
                    "Lộ trình đã có người theo, không xoá được. "
                            + "Hãy sửa nội dung thay vì xoá cả lộ trình.");
        }
        courseRepository.delete(course);
        log.info("Xoá khoá học courseId={} author={}", courseId, author.getUserId());
    }

    @Override
    @Transactional
    public CourseResponse submitForReview(String authorEmail, Integer courseId) {
        User author = requireUser(authorEmail);
        Course course = requireAuthoredCourse(courseId, author);

        // Khoá rỗng gửi duyệt là bắt Admin mở ra rồi từ chối — chặn ngay ở đây.
        if (lessonRepository.countByCourse_CourseId(courseId) == 0) {
            throw new BusinessException("Lộ trình chưa có chặng nào, chưa gửi duyệt được");
        }
        try {
            course.submitForReview();
        } catch (IllegalStateException e) {
            throw new BusinessException(course.getStatus() == CourseStatus.PENDING
                    ? "Lộ trình đang chờ duyệt rồi"
                    : "Lộ trình đã được xuất bản");
        }
        courseRepository.save(course);
        log.info("Gửi duyệt khoá học courseId={}", courseId);
        return describe(course, author);
    }

    @Override
    @Transactional
    public CourseDetailResponse addLesson(String authorEmail, Integer courseId,
                                          LessonCreateRequest request) {
        User author = requireUser(authorEmail);
        Course course = requireAuthoredCourse(courseId, author);
        requireEditable(course);

        long current = lessonRepository.countByCourse_CourseId(courseId);
        CourseLesson lesson = CourseLesson.builder()
                .course(course)
                .title(request.getTitle().trim())
                .lessonType(request.getLessonType() == null
                        ? LessonType.GRAMMAR : request.getLessonType())
                .content(request.getContent())
                .estimatedMinutes(request.getEstimatedMinutes())
                .deck(resolveDeck(request.getDeckId()))
                .exam(resolveExam(request.getExamId()))
                .minScorePercent(validPassPercent(request.getMinScorePercent()))
                // Để trống thì xếp xuống cuối — thứ tự người soạn thêm bài
                // gần như luôn là thứ tự họ muốn dạy.
                .orderNo(request.getOrderNo() == null ? (int) current + 1 : request.getOrderNo())
                .build();
        lessonRepository.save(lesson);

        course.markContentChanged();
        courseRepository.save(course);
        return detailFor(course, author);
    }

    @Override
    @Transactional
    public CourseDetailResponse updateLesson(String authorEmail, Integer courseId,
                                             Integer lessonId, LessonCreateRequest request) {
        User author = requireUser(authorEmail);
        Course course = requireAuthoredCourse(courseId, author);
        requireEditable(course);
        CourseLesson lesson = requireLessonOf(course, lessonId);

        lesson.setTitle(request.getTitle().trim());
        if (request.getLessonType() != null) {
            lesson.setLessonType(request.getLessonType());
        }
        lesson.setContent(request.getContent());
        lesson.setEstimatedMinutes(request.getEstimatedMinutes());
        lesson.setDeck(resolveDeck(request.getDeckId()));
        lesson.setExam(resolveExam(request.getExamId()));
        lesson.setMinScorePercent(validPassPercent(request.getMinScorePercent()));
        if (request.getOrderNo() != null) {
            lesson.setOrderNo(request.getOrderNo());
        }
        lessonRepository.save(lesson);

        course.markContentChanged();
        courseRepository.save(course);
        return detailFor(course, author);
    }

    @Override
    @Transactional
    public CourseDetailResponse deleteLesson(String authorEmail, Integer courseId,
                                             Integer lessonId) {
        User author = requireUser(authorEmail);
        Course course = requireAuthoredCourse(courseId, author);
        requireEditable(course);
        CourseLesson lesson = requireLessonOf(course, lessonId);

        // FK LessonCompletions → CourseLessons có ON DELETE CASCADE.
        lessonRepository.delete(lesson);
        course.markContentChanged();
        courseRepository.save(course);
        return detailFor(course, author);
    }

    // ── Admin ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getPendingCourses(String adminEmail) {
        User admin = requireAdmin(adminEmail);
        return describeAll(courseRepository.findByStatus(CourseStatus.PENDING), admin);
    }

    @Override
    @Transactional
    public CourseResponse approve(String adminEmail, Integer courseId) {
        User admin = requireAdmin(adminEmail);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lộ trình id=" + courseId));
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new BusinessException("Lộ trình không ở trạng thái chờ duyệt");
        }
        course.approve(admin, DbTime.now());
        courseRepository.save(course);
        log.info("Duyệt khoá học courseId={} admin={}", courseId, admin.getUserId());
        notificationService.notify(course.getAuthor(),
                com.example.demo.service.NotificationService.Kind.COURSE_APPROVED,
                "Lộ trình \"" + course.getTitle() + "\" đã được duyệt",
                "Lộ trình đã xuất bản — học viên tìm thấy và ghi danh được rồi.",
                "/teacher/courses");
        return describe(course, admin);
    }

    @Override
    @Transactional
    public CourseResponse reject(String adminEmail, Integer courseId,
                                 CourseReviewRequest request) {
        User admin = requireAdmin(adminEmail);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lộ trình id=" + courseId));
        if (course.getStatus() != CourseStatus.PENDING) {
            throw new BusinessException("Lộ trình không ở trạng thái chờ duyệt");
        }
        String note = request == null ? null : trimToNull(request.getNote());
        // Từ chối không kèm lý do thì tác giả chỉ biết là bị trả về, không biết
        // sửa gì, và sẽ gửi lại đúng bản cũ.
        if (note == null) {
            throw new BusinessException("Phải ghi lý do từ chối để tác giả biết cần sửa gì");
        }
        course.reject(admin, note, DbTime.now());
        courseRepository.save(course);
        log.info("Từ chối khoá học courseId={} admin={}", courseId, admin.getUserId());
        // Kèm nguyên văn lý do: tác giả cần biết sửa gì, không chỉ biết là bị trả về.
        notificationService.notify(course.getAuthor(),
                com.example.demo.service.NotificationService.Kind.COURSE_REJECTED,
                "Lộ trình \"" + course.getTitle() + "\" bị trả lại",
                "Lý do: " + note,
                "/teacher/courses");
        return describe(course, admin);
    }

    // ── Thí sinh ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> browsePublished(String userEmail, Integer levelId) {
        User user = requireUser(userEmail);
        return describeAll(courseRepository.findPublished(levelId), user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getEnrolledCourses(String userEmail) {
        User user = requireUser(userEmail);
        List<Integer> courseIds = enrollmentRepository.findCourseIdsByUserId(user.getUserId());
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return describeAll(courseRepository.findAllByIdWithDetails(courseIds), user);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourse(String userEmail, Integer courseId) {
        User user = requireUser(userEmail);
        return detailFor(requireVisibleCourse(courseId, user), user);
    }

    @Override
    @Transactional
    public CourseResponse enroll(String userEmail, Integer courseId) {
        User user = requireUser(userEmail);
        Course course = requireVisibleCourse(courseId, user);

        // Idempotent: bấm lại không tạo dòng thứ hai và không đặt lại StartedAt.
        if (!enrollmentRepository.existsById_UserIdAndId_CourseId(user.getUserId(), courseId)) {
            enrollmentRepository.save(CourseEnrollment.builder()
                    .id(new CourseEnrollmentKey(user.getUserId(), courseId))
                    .user(user)
                    .course(course)
                    .build());
            log.info("Ghi danh khoá học courseId={} userId={}", courseId, user.getUserId());
        }
        return describe(course, user);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDetailResponse getLesson(String userEmail, Integer courseId, Integer lessonId) {
        User user = requireUser(userEmail);
        Course course = requireVisibleCourse(courseId, user);

        List<CourseLesson> lessons =
                lessonRepository.findByCourse_CourseIdOrderByOrderNoAsc(courseId);
        int index = -1;
        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).getLessonId().equals(lessonId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new ResourceNotFoundException("Không tìm thấy chặng id=" + lessonId);
        }

        CourseLesson lesson = lessons.get(index);
        Deck deck = lesson.getDeck();
        Exam exam = lesson.getExam();
        Set<Integer> done = new HashSet<>(
                completionRepository.findCompletedLessonIds(user.getUserId(), courseId));
        if (!isPrivileged(course, user)) {
            requireUnlocked(lessons, index, done);
        }
        Double best = exam == null ? null : bestPercent(user, exam);

        return LessonDetailResponse.builder()
                .lessonId(lesson.getLessonId())
                .courseId(courseId)
                .courseTitle(course.getTitle())
                .orderNo(lesson.getOrderNo())
                .title(lesson.getTitle())
                .lessonType(lesson.getLessonType())
                .content(lesson.getContent())
                .estimatedMinutes(lesson.getEstimatedMinutes())
                .deckId(deck == null ? null : deck.getDeckId())
                .deckName(deck == null ? null : deck.getName())
                .examId(exam == null ? null : exam.getExamId())
                .examTitle(exam == null ? null : exam.getTitle())
                .completed(done.contains(lessonId))
                .minScorePercent(exam == null ? null : lesson.passPercent())
                .bestScorePercent(best)
                .examPassed(exam == null || (best != null && best >= lesson.passPercent()))
                // Điều hướng tính sẵn ở server: client không phải giữ cả danh
                // sách bài chỉ để biết bài kế tiếp là bài nào.
                .previousLessonId(index > 0 ? lessons.get(index - 1).getLessonId() : null)
                .nextLessonId(index < lessons.size() - 1
                        ? lessons.get(index + 1).getLessonId() : null)
                .build();
    }

    @Override
    @Transactional
    public CourseResponse completeLesson(String userEmail, Integer courseId, Integer lessonId) {
        User user = requireUser(userEmail);
        Course course = requireVisibleCourse(courseId, user);
        CourseLesson lesson = requireLessonOf(course, lessonId);

        // Hai luật biến danh sách bài thành LỘ TRÌNH: 1. Đi tuần tự — chặng trước chưa qua thì chặng này chưa mở.
        List<CourseLesson> lessons =
                lessonRepository.findByCourse_CourseIdOrderByOrderNoAsc(courseId);
        Set<Integer> done = new HashSet<>(
                completionRepository.findCompletedLessonIds(user.getUserId(), courseId));
        int index = lessons.indexOf(lesson);
        requireUnlocked(lessons, index, done);
        if (lesson.getExam() != null) {
            Double best = bestPercent(user, lesson.getExam());
            if (best == null || best < lesson.passPercent()) {
                throw new BusinessException("Chặng này có bài kiểm tra \"" + lesson.getExam().getTitle()
                        + "\": cần đạt từ " + lesson.passPercent() + "% để qua chặng. "
                        + (best == null ? "Bạn chưa làm bài này."
                                : "Điểm tốt nhất của bạn: " + Math.round(best) + "%."));
            }
        }

        // Đọc bài mà chưa ghi danh thì ghi danh luôn — bắt người ta quay lại
        // bấm một nút nữa chỉ để đếm được tiến độ là thừa.
        if (!enrollmentRepository.existsById_UserIdAndId_CourseId(user.getUserId(), courseId)) {
            enrollmentRepository.save(CourseEnrollment.builder()
                    .id(new CourseEnrollmentKey(user.getUserId(), courseId))
                    .user(user)
                    .course(course)
                    .build());
        }

        LessonCompletionKey key = new LessonCompletionKey(user.getUserId(), lessonId);
        if (!completionRepository.existsById(key)) {
            completionRepository.save(LessonCompletion.builder()
                    .id(key)
                    .user(user)
                    .lesson(lesson)
                    .build());
        }
        return describe(course, user);
    }

    // ── Hỗ trợ ──────────────────────────────────────────────────────────────

    private CourseDetailResponse detailFor(Course course, User viewer) {
        List<CourseLesson> lessons =
                lessonRepository.findByCourse_CourseIdOrderByOrderNoAsc(course.getCourseId());
        Set<Integer> done = new HashSet<>(completionRepository
                .findCompletedLessonIds(viewer.getUserId(), course.getCourseId()));
        boolean privileged = isPrivileged(course, viewer);

        List<LessonSummaryResponse> rows = new java.util.ArrayList<>(lessons.size());
        // Chặng đầu tiên chưa qua là chặng đang mở; mọi chặng sau nó còn khoá.
        boolean blocked = false;
        for (CourseLesson l : lessons) {
            Exam exam = l.getExam();
            rows.add(LessonSummaryResponse.builder()
                    .lessonId(l.getLessonId())
                    .orderNo(l.getOrderNo())
                    .title(l.getTitle())
                    .lessonType(l.getLessonType())
                    .estimatedMinutes(l.getEstimatedMinutes())
                    .hasDeck(l.getDeck() != null)
                    .hasExam(exam != null)
                    .completed(done.contains(l.getLessonId()))
                    .locked(!privileged && blocked)
                    .examId(exam == null ? null : exam.getExamId())
                    .examTitle(exam == null ? null : exam.getTitle())
                    .deckId(l.getDeck() == null ? null : l.getDeck().getDeckId())
                    .minScorePercent(exam == null ? null : l.passPercent())
                    .bestScorePercent(exam == null ? null : bestPercent(viewer, exam))
                    .build());
            if (!done.contains(l.getLessonId())) {
                blocked = true;
            }
        }

        return CourseDetailResponse.builder()
                .course(describe(course, viewer, lessons.size(), done.size(),
                        enrollmentRepository.existsById_UserIdAndId_CourseId(
                                viewer.getUserId(), course.getCourseId())))
                .lessons(rows)
                .build();
    }

    /** Dựng response cho một khoá đơn lẻ, tự đếm các con số cần thiết. */
    private CourseResponse describe(Course course, User viewer) {
        Integer courseId = course.getCourseId();
        return describe(course, viewer,
                lessonRepository.countByCourse_CourseId(courseId),
                completionRepository.findCompletedLessonIds(viewer.getUserId(), courseId).size(),
                enrollmentRepository.existsById_UserIdAndId_CourseId(viewer.getUserId(), courseId));
    }

    /** Dựng response cho cả một danh sách khoá. */
    private List<CourseResponse> describeAll(List<Course> courses, User viewer) {
        if (courses.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = courses.stream().map(Course::getCourseId).toList();

        Map<Integer, Long> totals = toCountMap(lessonRepository.countByCourseIds(ids));
        Map<Integer, Long> completed = toCountMap(
                completionRepository.countCompletedByCourseIds(viewer.getUserId(), ids));
        Set<Integer> enrolled =
                new HashSet<>(enrollmentRepository.findCourseIdsByUserId(viewer.getUserId()));

        return courses.stream()
                .map(c -> describe(c, viewer,
                        totals.getOrDefault(c.getCourseId(), 0L),
                        completed.getOrDefault(c.getCourseId(), 0L),
                        enrolled.contains(c.getCourseId())))
                .toList();
    }

    private CourseResponse describe(Course course, User viewer,
                                    long totalLessons, long completedLessons, boolean enrolled) {
        SubjectLevel level = course.getLevel();
        boolean isAuthor = course.isAuthoredBy(viewer.getUserId());

        // Chia ở server, không để client chia: hai bên chia riêng là hai chỗ có
        // thể chia cho 0 và hai cách làm tròn khác nhau.
        int percent = totalLessons == 0 ? 0
                : (int) Math.floor(completedLessons * 100.0 / totalLessons);

        return CourseResponse.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .description(course.getDescription())
                .levelId(level == null ? null : level.getLevelId())
                .levelName(level == null ? null : level.getLevelName())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .authorName(course.getAuthor() == null ? null : course.getAuthor().getFullName())
                .status(course.getStatus())
                // Lý do từ chối chỉ dành cho tác giả và Admin. Thí sinh không
                // cần biết nội dung trao đổi nội bộ giữa hai vai kia.
                .reviewNote(isAuthor || viewer.getRole() == Role.ADMIN
                        ? course.getReviewNote() : null)
                .reviewedByName(course.getReviewedBy() == null
                        ? null : course.getReviewedBy().getFullName())
                .reviewedAt(course.getReviewedAt())
                .totalLessons(totalLessons)
                .enrolledCount(enrollmentRepository.countById_CourseId(course.getCourseId()))
                .enrolled(enrolled)
                .completedLessons(completedLessons)
                .progressPercent(percent)
                .author(isAuthor)
                .build();
    }

    private Map<Integer, Long> toCountMap(List<Object[]> rows) {
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /** Khoá của chính tác giả, ở bất kỳ trạng thái nào. */
    private Course requireAuthoredCourse(Integer courseId, User author) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lộ trình id=" + courseId));
        if (!course.isAuthoredBy(author.getUserId())) {
            // 404 chứ không 403: người ngoài không cần biết khoá đó có tồn tại.
            throw new ResourceNotFoundException("Không tìm thấy lộ trình id=" + courseId);
        }
        return course;
    }

    /** Khoá mà người này được xem. */
    private Course requireVisibleCourse(Integer courseId, User viewer) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lộ trình id=" + courseId));
        boolean allowed = course.isPublished()
                || course.isAuthoredBy(viewer.getUserId())
                || viewer.getRole() == Role.ADMIN
                || enrollmentRepository.existsById_UserIdAndId_CourseId(
                        viewer.getUserId(), courseId);
        if (!allowed) {
            throw new ResourceNotFoundException("Không tìm thấy lộ trình id=" + courseId);
        }
        return course;
    }

    private void requireEditable(Course course) {
        if (!course.getStatus().isEditableByAuthor()) {
            throw new BusinessException(
                    "Lộ trình đang chờ duyệt, không sửa được. "
                            + "Đợi Admin xem xong rồi sửa tiếp.");
        }
    }

    private CourseLesson requireLessonOf(Course course, Integer lessonId) {
        CourseLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chặng id=" + lessonId));
        // Bài phải thuộc đúng khoá trên đường dẫn.
        if (!lesson.getCourse().getCourseId().equals(course.getCourseId())) {
            throw new ResourceNotFoundException("Không tìm thấy chặng id=" + lessonId);
        }
        return lesson;
    }

    private SubjectLevel resolveLevel(Integer levelId) {
        if (levelId == null) {
            return null;
        }
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy trình độ id=" + levelId));
    }

    private Deck resolveDeck(Integer deckId) {
        if (deckId == null) {
            return null;
        }
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bộ thẻ id=" + deckId));
        // Bộ riêng của học viên không được gắn vào lộ trình.
        if (!deck.isSystemDeck()) {
            throw new ResourceNotFoundException("Không tìm thấy bộ thẻ id=" + deckId);
        }
        return deck;
    }

    private Exam resolveExam(Integer examId) {
        if (examId == null) {
            return null;
        }
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bài thi id=" + examId));
        // Đề chỉ nằm trong phòng thì người theo lộ trình không vào làm được (không ở trong phòng đó)
        if (!Boolean.TRUE.equals(exam.getIsPublic())) {
            throw new BusinessException("Bài kiểm tra của chặng phải là đề tự do (công khai). "
                    + "Đề \"" + exam.getTitle() + "\" chỉ làm được trong phòng thi, "
                    + "người theo lộ trình sẽ không vào làm được.");
        }
        return exam;
    }

    private Integer validPassPercent(Integer percent) {
        if (percent == null) {
            return null;
        }
        if (percent < 1 || percent > 100) {
            throw new BusinessException("Điểm tối thiểu để qua chặng phải từ 1 đến 100%");
        }
        return percent;
    }

    /** Tác giả và Admin đọc được mọi chặng — họ soạn và duyệt, không đi lộ trình. */
    private boolean isPrivileged(Course course, User viewer) {
        return course.isAuthoredBy(viewer.getUserId()) || viewer.getRole() == Role.ADMIN;
    }

    /** Chặng ở vị trí {@code index} chỉ mở khi mọi chặng đứng trước đã qua. */
    private void requireUnlocked(List<CourseLesson> lessons, int index, Set<Integer> done) {
        for (int i = 0; i < index; i++) {
            if (!done.contains(lessons.get(i).getLessonId())) {
                throw new BusinessException("Chặng này chưa mở. Hãy qua chặng "
                        + lessons.get(i).getOrderNo() + " — \"" + lessons.get(i).getTitle()
                        + "\" trước.");
            }
        }
    }

    /** Điểm tốt nhất (% điểm tối đa) của người này ở một đề, trên các lượt đã nộp. */
    private Double bestPercent(User user, Exam exam) {
        java.math.BigDecimal best = null;
        for (var s : submissionRepository.findByExam_ExamIdAndStudent_UserIdIn(
                exam.getExamId(), List.of(user.getUserId()))) {
            if (s.isInProgress() || s.getTotalScore() == null) {
                continue;
            }
            if (best == null || s.getTotalScore().compareTo(best) > 0) {
                best = s.getTotalScore();
            }
        }
        if (best == null) {
            return null;
        }
        java.math.BigDecimal max = java.math.BigDecimal.ZERO;
        for (var q : examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId())) {
            max = max.add(q.getPoints() != null ? q.getPoints() : java.math.BigDecimal.ONE);
        }
        if (max.signum() == 0) {
            return null;
        }
        return best.multiply(java.math.BigDecimal.valueOf(100))
                .divide(max, 1, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private User requireAuthor(String email) {
        User user = requireUser(email);
        if (user.getRole() != Role.TEACHER && user.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Chỉ người ra đề mới soạn được lộ trình ôn tập");
        }
        return user;
    }

    private User requireAdmin(String email) {
        User user = requireUser(email);
        if (user.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Chỉ quản trị viên mới duyệt được lộ trình ôn tập");
        }
        return user;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
