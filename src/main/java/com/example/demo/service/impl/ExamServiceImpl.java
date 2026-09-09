package com.example.demo.service.impl;

import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.MemberStatus;
import com.example.demo.domain.model.Room;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.RoomExamGroup;
import com.example.demo.dto.response.ExamResponse;
import com.example.demo.dto.response.PracticeExamsResponse;
import com.example.demo.dto.response.PracticeLevelOption;
import com.example.demo.dto.response.StudentExamBoardResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.RoomRepository;
import com.example.demo.repository.RoomMemberRepository;
import com.example.demo.repository.RoomExamRepository;
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
 * Cài đặt phần "chọn đề để làm" của thí sinh.
 *
 * Ba lối vào (trang chủ, đề của một lớp, đề luyện tập) dùng chung một bộ hàm
 * dựng {@link ExamResponse}, nên trạng thái của một đề luôn được tính giống
 * nhau bất kể thí sinh mở nó từ màn hình nào.
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
    private final RoomMemberRepository roomMemberRepository;
    private final RoomExamRepository roomExamRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    // ── Trang chủ ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StudentExamBoardResponse getExamBoard(String studentEmail) {
        User student = requireStudent(studentEmail);
        LocalDateTime now = LocalDateTime.now();

        List<Integer> roomIds = roomMemberRepository.findActiveRoomIdsByUserId(student.getUserId());
        List<Room> rooms = roomIds.isEmpty()
                ? List.of()
                : roomRepository.findAllByIdWithDetails(roomIds);
        List<Exam> roomExams = roomIds.isEmpty()
                ? List.of()
                : examRepository.findByRoomIdIn(roomIds);

        // Trình độ của các phòng đang tham gia — cũng chính là bộ lọc mặc định
        // cho phần luyện tập bên dưới.
        //
        // Chưa vào phòng nào thì không có trình độ nào để lọc. Lúc đó KHÔNG kéo
        // toàn bộ đề công khai trong hệ thống về chỉ để hiện sáu dòng xem trước —
        // lấy đúng sáu đề mới nhất. Đổi lại là phần xem trước của trường hợp này
        // sắp theo "mới nhất" chứ không theo mức độ gấp; chấp nhận được vì chưa
        // vào phòng nào thì cũng chưa có bài nào được giao.
        Set<Integer> enrolledLevelIds = levelIdsOf(rooms);
        List<Exam> practiceExams = enrolledLevelIds.isEmpty()
                ? examRepository.findPracticeExams(null, null,
                        PageRequest.of(0, PRACTICE_PREVIEW_SIZE,
                                Sort.by(Sort.Direction.DESC, "examId"))).getContent()
                : examRepository.findPracticeExamsByLevelIdIn(enrolledLevelIds);

        // Một lượt đếm câu hỏi và một lượt đọc bài làm cho CẢ hai nhóm, thay vì
        // mỗi nhóm một lượt.
        List<Exam> allExams = new ArrayList<>(roomExams.size() + practiceExams.size());
        allExams.addAll(roomExams);
        allExams.addAll(practiceExams);
        Context ctx = contextOf(student, allExams, now);

        // Gom đề theo phòng. Khác thời còn lớp: một đề gắn được vào nhiều phòng,
        // nên phải tra bảng nối thay vì đọc một cột trên chính đề. Đề nằm ở hai
        // phòng mà thí sinh đều tham gia sẽ xuất hiện ở cả hai nhóm — đúng như
        // người dùng mong đợi khi họ nhìn vào từng phòng.
        Map<Integer, List<Integer>> examIdsByRoom = new HashMap<>();
        if (!roomIds.isEmpty()) {
            for (Object[] pair : roomExamRepository.findExamRoomPairs(roomIds)) {
                examIdsByRoom.computeIfAbsent((Integer) pair[1], k -> new ArrayList<>())
                        .add((Integer) pair[0]);
            }
        }
        Map<Integer, ExamResponse> roomExamRows = new HashMap<>();
        for (Exam exam : roomExams) {
            roomExamRows.put(exam.getExamId(),
                    toResponse(exam, ExamResponse.Source.ROOM, ctx));
        }

        // Phòng chưa có đề nào vẫn xuất hiện với danh sách rỗng — thí sinh cần
        // thấy phòng mình ở đó, không phải thấy nó biến mất.
        List<RoomExamGroup> groups = new ArrayList<>(rooms.size());
        for (Room room : rooms) {
            List<ExamResponse> rows = new ArrayList<>();
            for (Integer examId : examIdsByRoom.getOrDefault(room.getRoomId(), List.of())) {
                ExamResponse row = roomExamRows.get(examId);
                if (row != null) {
                    rows.add(row);
                }
            }
            rows.sort(URGENCY);
            groups.add(toGroup(room, rows, countPending(rows)));
        }
        // Đếm trên tập đề DUY NHẤT, không cộng dồn số của từng phòng: một đề
        // nằm ở hai phòng sẽ bị tính hai lần và huy hiệu "còn n bài" nói dối.
        int pendingTotal = countPending(new ArrayList<>(roomExamRows.values()));
        groups.sort(Comparator.comparing(RoomExamGroup::getPendingCount).reversed()
                .thenComparing(RoomExamGroup::getRoomName, Comparator.nullsLast(String::compareTo)));

        List<ExamResponse> practiceRows = toResponses(practiceExams, ExamResponse.Source.PRACTICE, ctx);
        practiceRows.sort(URGENCY);
        boolean truncated = practiceRows.size() > PRACTICE_PREVIEW_SIZE;
        if (truncated) {
            practiceRows = new ArrayList<>(practiceRows.subList(0, PRACTICE_PREVIEW_SIZE));
        }

        return StudentExamBoardResponse.builder()
                .rooms(groups)
                .practice(practiceRows)
                .pendingCount(pendingTotal)
                .practiceTruncated(truncated)
                .serverTime(now)
                .build();
    }

    // ── Đề của một phòng ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ExamResponse> getRoomExams(Integer roomId, String studentEmail) {
        User student = requireStudent(studentEmail);

        // 404 chứ không 403: người ngoài phòng không được biết phòng đó có tồn
        // tại. Cùng một luật với requireCanTakeExam lúc bắt đầu thi, nên không
        // có phòng nào nhìn thấy được ở đây mà bấm vào lại bị chặn ở bước sau.
        if (!roomMemberRepository.existsById_RoomIdAndId_UserIdAndStatus(
                roomId, student.getUserId(), MemberStatus.ACTIVE)) {
            throw new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Exam> exams = examRepository.findByRoomIdIn(List.of(roomId));
        List<ExamResponse> rows = toResponses(
                exams, ExamResponse.Source.ROOM, contextOf(student, exams, now));
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

        // Thí sinh chưa chọn gì thì server chọn hộ một trình độ để mở màn, thay
        // vì đổ ra toàn bộ đề của mọi trình độ. Chọn theo phòng em đang tham gia và
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
        // tính từ bài làm của từng thí sinh nên chỉ có trong bộ nhớ, mà sắp trong
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

    /** Trình độ đầu tiên thí sinh đang học mà có ít nhất một đề luyện tập. */
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
     * lượng, và đánh dấu trình độ thí sinh đang học.
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
     * thí sinh, mỗi thứ đọc đúng một lần.
     *
     * Gom lại thành một object thay vì truyền hai Map rời qua từng hàm, để chỗ
     * gọi không thể lỡ tay truyền nhầm thứ tự hai tham số cùng kiểu.
     */
    private record Context(Map<Integer, Long> questionCounts,
                           Map<Integer, ExamSubmission> submissions,
                           Map<Integer, Long> attemptCounts,
                           LocalDateTime now) {
    }

    private Context contextOf(User student, List<Exam> exams, LocalDateTime now) {
        Map<Integer, ExamSubmission> latest = new HashMap<>();
        Map<Integer, Long> attempts = new HashMap<>();
        collectSubmissions(student, latest, attempts);
        return new Context(questionCountsOf(exams), latest, attempts, now);
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
        SubjectLevel level = exam.getLevel();
        ExamSubmission submission = ctx.submissions().get(exam.getExamId());
        int totalQuestions = ctx.questionCounts()
                .getOrDefault(exam.getExamId(), 0L).intValue();

        long attemptsUsed = ctx.attemptCounts().getOrDefault(exam.getExamId(), 0L);
        ExamResponse.Availability availability =
                resolveAvailability(exam, submission, totalQuestions, attemptsUsed, ctx.now());
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
                .levelId(level == null ? null : level.getLevelId())
                .levelName(level == null ? null : level.getLevelName())
                .subjectId(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectId())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .teacherName(exam.getCreatedBy() == null ? null : exam.getCreatedBy().getFullName())
                .availability(availability)
                .maxAttempts(exam.getMaxAttempts())
                .attemptsUsed(attemptsUsed)
                .attemptsRemaining(exam.attemptsRemaining(attemptsUsed))
                .canRetake(availability == ExamResponse.Availability.RETAKEABLE)
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

    private RoomExamGroup toGroup(Room room, List<ExamResponse> exams, int pending) {
        SubjectLevel level = room.getLevel();
        return RoomExamGroup.builder()
                .roomId(room.getRoomId())
                .roomName(room.getName())
                .ownerName(room.getOwner() == null ? null : room.getOwner().getFullName())
                .levelName(level == null ? null : level.getLevelName())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .pendingCount(pending)
                .exams(exams)
                .build();
    }

    private ExamResponse.Availability resolveAvailability(Exam exam, ExamSubmission submission,
                                                          int totalQuestions, long attemptsUsed,
                                                          LocalDateTime now) {
        if (submission != null) {
            boolean finished = !submission.isInProgress() || submission.isExpiredAt(now);
            if (!finished) {
                return ExamResponse.Availability.IN_PROGRESS;
            }
            // Đã nộp. Còn được làm lại hay không là hai điều kiện tách rời: giáo
            // viên còn cho lượt, VÀ đề vẫn đang trong giờ mở. Hết một trong hai
            // thì bài chỉ còn để xem lại.
            return exam.allowsAttempt(attemptsUsed) && examWindowOpen(exam, now)
                    ? ExamResponse.Availability.RETAKEABLE
                    : ExamResponse.Availability.SUBMITTED;
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

    /** Đề đang trong khung giờ cho làm bài. Không đặt mốc nào = luôn mở. */
    private static boolean examWindowOpen(Exam exam, LocalDateTime now) {
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            return false;
        }
        return exam.getEndTime() == null || now.isBefore(exam.getEndTime());
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
            case NO_QUESTIONS -> 3;     // người ra đề chưa gắn câu hỏi
            // Đã làm xong, nhưng còn lượt để luyện lại — vẫn là việc thí sinh CÓ
            // THỂ làm, nên đứng trên nhóm chỉ để xem lại điểm.
            case RETAKEABLE -> 4;
            case SUBMITTED -> 5;        // xong rồi, chỉ để xem lại điểm
            case CLOSED -> 6;           // hết hạn mà không làm
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

    /** Số đề thí sinh còn phải làm: đang dở, đang mở, hoặc sắp mở. */
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
        List<Integer> roomIds = roomMemberRepository.findActiveRoomIdsByUserId(student.getUserId());
        if (roomIds.isEmpty()) {
            return Set.of();
        }
        return levelIdsOf(roomRepository.findAllByIdWithDetails(roomIds));
    }

    /** LinkedHashSet để thứ tự trình độ ổn định giữa các lần gọi. */
    private Set<Integer> levelIdsOf(List<Room> rooms) {
        Set<Integer> levelIds = new LinkedHashSet<>();
        for (Room room : rooms) {
            if (room.getLevel() != null) {
                levelIds.add(room.getLevel().getLevelId());
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

    /**
     * Bài làm của thí sinh, tra theo ExamID — một lượt đọc DB cho cả hai thứ màn
     * hình danh sách cần biết.
     *
     * Từ v1.2.0 một đề có thể có nhiều lượt làm, nên phải nói rõ lấy lượt nào:
     *
     *   - {@code latest} giữ lượt có AttemptNumber lớn nhất. Danh sách đề hiện
     *     tình trạng HIỆN TẠI của thí sinh ("đang làm dở", "vừa nộp xong"), mà
     *     tình trạng đó thuộc về lượt gần nhất chứ không phải lượt điểm cao nhất.
     *   - {@code attempts} đếm tổng số lượt, để tính còn được làm lại mấy lần.
     */
    private void collectSubmissions(User student,
                                    Map<Integer, ExamSubmission> latest,
                                    Map<Integer, Long> attempts) {
        for (ExamSubmission submission : submissionRepository.findByStudentUserId(student.getUserId())) {
            Integer examId = submission.getExam().getExamId();
            attempts.merge(examId, 1L, Long::sum);
            ExamSubmission current = latest.get(examId);
            if (current == null || attemptOf(submission) >= attemptOf(current)) {
                latest.put(examId, submission);
            }
        }
    }

    /** Dữ liệu tạo trước v1.2.0 có thể chưa có AttemptNumber; coi như lượt 1. */
    private static int attemptOf(ExamSubmission submission) {
        return submission.getAttemptNumber() == null ? 1 : submission.getAttemptNumber();
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ thí sinh mới xem được danh sách đề thi của mình");
        }
        return user;
    }
}
