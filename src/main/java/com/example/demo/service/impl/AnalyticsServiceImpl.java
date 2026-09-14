package com.example.demo.service.impl;

import com.example.demo.domain.enums.RoomPhase;
import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.Room;
import com.example.demo.domain.model.RoomMember;
import com.example.demo.domain.model.SubmissionDetail;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.QuestionStatRow;
import com.example.demo.dto.response.TagStatRow;
import com.example.demo.dto.response.TeacherOverviewResponse;
import com.example.demo.dto.response.TeacherStudentRow;
import com.example.demo.dto.response.TeacherSubmissionRow;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.RoomExamRepository;
import com.example.demo.repository.RoomMemberRepository;
import com.example.demo.repository.RoomRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AnalyticsService;
import com.example.demo.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Xem {@link AnalyticsService} cho mục đích của từng phương thức. */
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    /** Bài nộp gần đây hiện trên màn tổng quan. */
    private static final int RECENT_LIMIT = 8;

    /** Số câu khó nhất hiện trên màn tổng quan. */
    private static final int HARDEST_LIMIT = 5;

    /** Câu phải có ít nhất chừng này lượt trả lời mới được gọi là "câu khó". */
    private static final int MIN_ANSWERS_FOR_HARDEST = 3;

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final SubmissionDetailRepository detailRepository;
    private final RoomRepository roomRepository;
    private final RoomExamRepository roomExamRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SubmissionService submissionService;

    // ── Tổng quan ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public TeacherOverviewResponse overview(String teacherEmail) {
        User teacher = requireTeacher(teacherEmail);
        List<Exam> myExams = examRepository.findByCreatedByUserId(teacher.getUserId());
        List<Room> myRooms = roomRepository.findByOwner(teacher.getUserId());
        LocalDateTime now = LocalDateTime.now();

        long withoutQuestions = myExams.stream()
                .filter(e -> examQuestionRepository.countByExam_ExamId(e.getExamId()) == 0)
                .count();

        // Phòng "đang thi" tính bằng đúng hàm mà thí sinh dùng để biết mình vào được chưa.
        long inProgress = myRooms.stream()
                .filter(r -> r.phaseAt(now, longestExamMinutes(r)) == RoomPhase.IN_PROGRESS)
                .count();

        Set<String> studentIds = new HashSet<>();
        for (Room room : myRooms) {
            studentIds.addAll(activeMemberIds(room.getRoomId()));
        }

        List<ExamSubmission> allSubmissions = new ArrayList<>();
        for (Exam exam : myExams) {
            allSubmissions.addAll(submissionRepository.findByExamExamId(exam.getExamId()));
        }
        List<ExamSubmission> finished = allSubmissions.stream()
                .filter(s -> s.getStatus() != SubmissionStatus.IN_PROGRESS)
                .toList();
        LocalDateTime weekAgo = now.minusDays(7);

        List<TeacherSubmissionRow> recent = finished.stream()
                .sorted(newestFirst())
                .limit(RECENT_LIMIT)
                .map(this::toRow)
                .toList();

        // Câu khó nhất gộp trên MỌI đề của người ra đề.
        List<QuestionStatRow> hardest = new ArrayList<>();
        for (Exam exam : myExams) {
            hardest.addAll(questionStats(exam, null));
        }
        hardest = hardest.stream()
                .filter(q -> q.getAnswered() >= MIN_ANSWERS_FOR_HARDEST && q.getCorrectPercent() != null)
                .sorted(Comparator.comparingInt(QuestionStatRow::getCorrectPercent))
                .limit(HARDEST_LIMIT)
                .toList();

        return TeacherOverviewResponse.builder()
                .totalExams(myExams.size())
                .publicExams(myExams.stream().filter(e -> Boolean.TRUE.equals(e.getIsPublic())).count())
                .examsWithoutQuestions(withoutQuestions)
                .totalRooms(myRooms.size())
                .roomsInProgress(inProgress)
                .totalStudents(studentIds.size())
                .submissionsTotal(finished.size())
                .submissionsLast7Days(finished.stream()
                        .filter(s -> s.getSubmittedAt() != null && s.getSubmittedAt().isAfter(weekAgo))
                        .count())
                .sessionsAtRisk(allSubmissions.stream()
                        .filter(s -> s.getStatus() == SubmissionStatus.IN_PROGRESS
                                && Boolean.TRUE.equals(s.getAtRiskStatus()))
                        .count())
                .hardestQuestions(hardest)
                .recentSubmissions(recent)
                .build();
    }

    // ── Danh sách bài nộp ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<TeacherSubmissionRow> submissionsOfExam(Integer examId, String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        return submissionRepository.findFinishedWithStudentByExamId(exam.getExamId()).stream()
                .sorted(newestFirst())
                .map(this::toRow)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherSubmissionRow> submissionsOfRoom(Integer roomId, String teacherEmail) {
        Room room = requireOwnedRoom(roomId, teacherEmail);
        Set<String> memberIds = activeMemberIds(room.getRoomId());
        if (memberIds.isEmpty()) {
            return List.of();
        }

        List<TeacherSubmissionRow> rows = new ArrayList<>();
        for (var roomExam : roomExamRepository.findById_RoomIdOrderByOrderNoAsc(room.getRoomId())) {
            Integer examId = roomExam.getId().getExamId();
            // Lọc theo thành viên phòng: một đề công khai cũng có thể được người ngoài phòng làm.
            submissionRepository.findByExam_ExamIdAndStudent_UserIdIn(examId, memberIds).stream()
                    .filter(s -> s.getStatus() != SubmissionStatus.IN_PROGRESS)
                    .map(this::toRow)
                    .forEach(rows::add);
        }
        rows.sort(Comparator.comparing(TeacherSubmissionRow::getSubmittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public ExamResultResponse paperOfSubmission(Integer submissionId, String teacherEmail) {
        ExamSubmission session = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bài làm id=" + submissionId));
        // Quyền xem = "đề này do tôi soạn".
        requireOwnedExam(session.getExam().getExamId(), teacherEmail);
        return submissionService.getPaperForReview(submissionId);
    }

    // ── Thống kê câu hỏi & tag ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<QuestionStatRow> questionStatsOfExam(Integer examId, Integer roomId, String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        List<QuestionStatRow> rows = new ArrayList<>(questionStats(exam, scopeIds(roomId, teacherEmail)));
        // Câu sai nhiều nhất lên đầu — đó là thứ mang lên bảng ở buổi chữa đề.
        rows.sort(Comparator.comparing(QuestionStatRow::getCorrectPercent,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagStatRow> tagStatsOfExam(Integer examId, Integer roomId, String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        Set<String> scope = scopeIds(roomId, teacherEmail);

        List<Object[]> raw;
        if (scope == null) {
            raw = detailRepository.tagStatsByExam(exam.getExamId());
        } else if (scope.isEmpty()) {
            raw = List.of();
        } else {
            raw = detailRepository.tagStatsByExamAndStudents(exam.getExamId(), scope);
        }

        List<TagStatRow> rows = new ArrayList<>();
        for (Object[] r : raw) {
            long answered = ((Number) r[2]).longValue();
            long correct = r[3] == null ? 0L : ((Number) r[3]).longValue();
            rows.add(TagStatRow.builder()
                    .tagId((Integer) r[0])
                    .tagName((String) r[1])
                    .answered(answered)
                    .correct(correct)
                    .correctPercent(percent(correct, answered))
                    .build());
        }
        // Tag yếu nhất lên đầu — đó là kỹ năng cần dạy lại.
        rows.sort(Comparator.comparing(TagStatRow::getCorrectPercent,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    // ── Thí sinh ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<TeacherStudentRow> students(String teacherEmail) {
        User teacher = requireTeacher(teacherEmail);
        List<Room> myRooms = roomRepository.findByOwner(teacher.getUserId());

        // Gom theo thí sinh: một người có thể ở nhiều phòng của cùng người ra đề.
        Map<String, User> people = new LinkedHashMap<>();
        Map<String, Set<String>> roomsOf = new HashMap<>();
        Map<String, Set<Integer>> examsOf = new HashMap<>();

        for (Room room : myRooms) {
            List<Integer> examIds = roomExamRepository
                    .findById_RoomIdOrderByOrderNoAsc(room.getRoomId()).stream()
                    .map(re -> re.getId().getExamId())
                    .toList();
            for (RoomMember member : roomMemberRepository.findActiveMembers(room.getRoomId())) {
                String userId = member.getId().getUserId();
                people.putIfAbsent(userId, member.getUser());
                roomsOf.computeIfAbsent(userId, k -> new LinkedHashSet<>()).add(room.getName());
                examsOf.computeIfAbsent(userId, k -> new LinkedHashSet<>()).addAll(examIds);
            }
        }

        List<TeacherStudentRow> rows = new ArrayList<>();
        for (Map.Entry<String, User> entry : people.entrySet()) {
            String userId = entry.getKey();
            User person = entry.getValue();
            Set<Integer> assigned = examsOf.getOrDefault(userId, Set.of());

            Set<Integer> submittedExamIds = new HashSet<>();
            List<Integer> percents = new ArrayList<>();
            LocalDateTime last = null;

            for (Integer examId : assigned) {
                BigDecimal max = maxScoreOf(examId);
                for (ExamSubmission s : submissionRepository
                        .findByExam_ExamIdAndStudent_UserIdIn(examId, List.of(userId))) {
                    if (s.getStatus() == SubmissionStatus.IN_PROGRESS) {
                        continue;
                    }
                    submittedExamIds.add(examId);
                    Integer p = percentOf(s, max);
                    if (p != null) {
                        percents.add(p);
                    }
                    if (s.getSubmittedAt() != null
                            && (last == null || s.getSubmittedAt().isAfter(last))) {
                        last = s.getSubmittedAt();
                    }
                }
            }

            rows.add(TeacherStudentRow.builder()
                    .userId(userId)
                    .fullName(person.getFullName())
                    .email(person.getEmail())
                    .avatarUrl(person.getAvatarUrl())
                    .roomNames(List.copyOf(roomsOf.getOrDefault(userId, Set.of())))
                    .assignedExams(assigned.size())
                    .submittedExams(submittedExamIds.size())
                    .averagePercent(percents.isEmpty() ? null
                            : (int) Math.round(percents.stream()
                                    .mapToInt(Integer::intValue).average().orElse(0)))
                    .lastSubmittedAt(last)
                    .build());
        }

        // Người làm ít nhất lên đầu: đó là người cần nhắc, còn người đã làm đủ
        // thì không cần ai chú ý tới.
        rows.sort(Comparator.comparingInt(TeacherStudentRow::getSubmittedExams)
                .thenComparing(TeacherStudentRow::getFullName,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    // ── Phần dùng chung ─────────────────────────────────────────────────────

    private List<QuestionStatRow> questionStats(Exam exam, Set<String> scope) {
        List<Object[]> raw;
        if (scope == null) {
            raw = detailRepository.questionStatsByExam(exam.getExamId());
        } else if (scope.isEmpty()) {
            raw = List.of();
        } else {
            raw = detailRepository.questionStatsByExamAndStudents(exam.getExamId(), scope);
        }

        Map<Integer, long[]> counts = new HashMap<>();
        for (Object[] r : raw) {
            long answered = ((Number) r[1]).longValue();
            long correct = r[2] == null ? 0L : ((Number) r[2]).longValue();
            counts.put((Integer) r[0], new long[]{answered, correct});
        }

        List<QuestionStatRow> rows = new ArrayList<>();
        for (ExamQuestion eq : examQuestionRepository
                .findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId())) {
            Integer questionId = eq.getId().getQuestionId();
            long[] c = counts.getOrDefault(questionId, new long[]{0L, 0L});
            rows.add(QuestionStatRow.builder()
                    .questionId(questionId)
                    .questionOrder(eq.getQuestionOrder())
                    // Nội dung theo snapshot: đúng thứ thí sinh đã nhìn thấy, kể
                    // cả khi câu trong ngân hàng đã được sửa sau đó.
                    .content(eq.resolveContent())
                    .tags(eq.getQuestion().getTags().stream()
                            .map(t -> t.getTagName())
                            .toList())
                    .answered(c[0])
                    .correct(c[1])
                    .correctPercent(percent(c[1], c[0]))
                    .build());
        }
        return rows;
    }

    /** Danh sách thí sinh để giới hạn phạm vi thống kê. */
    private Set<String> scopeIds(Integer roomId, String teacherEmail) {
        if (roomId == null) {
            return null;
        }
        Room room = requireOwnedRoom(roomId, teacherEmail);
        return activeMemberIds(room.getRoomId());
    }

    private Set<String> activeMemberIds(Integer roomId) {
        Set<String> ids = new LinkedHashSet<>();
        for (RoomMember m : roomMemberRepository.findActiveMembers(roomId)) {
            ids.add(m.getId().getUserId());
        }
        return ids;
    }

    private Integer longestExamMinutes(Room room) {
        return roomExamRepository.findById_RoomIdOrderByOrderNoAsc(room.getRoomId()).stream()
                .map(re -> examRepository.findById(re.getId().getExamId()).orElse(null))
                .filter(e -> e != null && e.getDurationMinutes() != null)
                .map(Exam::getDurationMinutes)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private BigDecimal maxScoreOf(Integer examId) {
        return examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(examId).stream()
                .map(q -> q.getPoints() == null ? BigDecimal.ONE : q.getPoints())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private TeacherSubmissionRow toRow(ExamSubmission s) {
        Exam exam = s.getExam();
        List<ExamQuestion> questions =
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId());
        BigDecimal maxScore = questions.stream()
                .map(q -> q.getPoints() == null ? BigDecimal.ONE : q.getPoints())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SubmissionDetail> details =
                detailRepository.findBySubmission_SubmissionId(s.getSubmissionId());
        int answered = (int) details.stream()
                .filter(d -> d.getSelectedSnapshotAnswer() != null
                        || d.getSelectedAnswer() != null
                        || d.getEssayResponse() != null)
                .count();
        int correct = (int) details.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsCorrect()))
                .count();

        User student = s.getStudent();
        return TeacherSubmissionRow.builder()
                .submissionId(s.getSubmissionId())
                .studentId(student.getUserId())
                .studentName(student.getFullName())
                .studentEmail(student.getEmail())
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
                .attemptNumber(s.getAttemptNumber())
                .status(s.getStatus())
                .autoSubmitted(Boolean.TRUE.equals(s.getAutoSubmitted()))
                .startedAt(s.getStartedAt())
                .submittedAt(s.getSubmittedAt())
                .durationMinutes(s.getStartedAt() == null || s.getSubmittedAt() == null ? null
                        : Duration.between(s.getStartedAt(), s.getSubmittedAt()).toMinutes())
                .totalScore(s.getTotalScore())
                .maxScore(maxScore)
                .percent(percentOf(s, maxScore))
                .totalQuestions(questions.size())
                .answeredQuestions(answered)
                .correctAnswers(correct)
                .build();
    }

    private Integer percentOf(ExamSubmission s, BigDecimal maxScore) {
        if (maxScore == null || maxScore.signum() == 0 || s.getTotalScore() == null) {
            return null;
        }
        return s.getTotalScore()
                .multiply(BigDecimal.valueOf(100))
                .divide(maxScore, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static Integer percent(long part, long whole) {
        return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
    }

    private static Comparator<ExamSubmission> newestFirst() {
        return Comparator.comparing(ExamSubmission::getSubmittedAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    // ── Phân quyền ──────────────────────────────────────────────────────────

    private User requireTeacher(String teacherEmail) {
        return userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + teacherEmail));
    }

    /** Đề của người khác thì với người gọi, nó KHÔNG TỒN TẠI. */
    private Exam requireOwnedExam(Integer examId, String teacherEmail) {
        User teacher = requireTeacher(teacherEmail);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        if (!exam.getCreatedBy().getUserId().equals(teacher.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId);
        }
        return exam;
    }

    private Room requireOwnedRoom(Integer roomId, String teacherEmail) {
        User teacher = requireTeacher(teacherEmail);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId));
        if (!room.getOwner().getUserId().equals(teacher.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId);
        }
        return room;
    }
}
