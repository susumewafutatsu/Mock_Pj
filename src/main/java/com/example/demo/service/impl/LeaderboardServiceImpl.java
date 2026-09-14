package com.example.demo.service.impl;

import com.example.demo.domain.enums.MemberStatus;
import com.example.demo.domain.enums.RoomPhase;
import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.Room;
import com.example.demo.domain.model.RoomExam;
import com.example.demo.domain.model.RoomMember;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.LeaderboardResponse;
import com.example.demo.dto.response.LeaderboardResponse.Board;
import com.example.demo.dto.response.LeaderboardResponse.Row;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.RoomExamRepository;
import com.example.demo.repository.RoomMemberRepository;
import com.example.demo.repository.RoomRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.LeaderboardService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private static final BigDecimal DEFAULT_POINTS = new BigDecimal("1.00");

    /** Đề tự do có thể có hàng trăm người làm — trả tốp này, cộng dòng của chính mình. */
    private static final int EXAM_TOP_N = 20;

    /** Điểm cao hơn trước; bằng điểm thì làm nhanh hơn trước. */
    private static final Comparator<ExamSubmission> BEST_FIRST =
            Comparator.comparing(ExamSubmission::getTotalScore,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(LeaderboardServiceImpl::durationSeconds);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomExamRepository roomExamRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final SubmissionDetailRepository detailRepository;
    private final UserRepository userRepository;

    // ── Phòng thi ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public LeaderboardResponse roomLeaderboard(String viewerEmail, Integer roomId) {
        User viewer = requireUser(viewerEmail);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId));

        LocalDateTime now = LocalDateTime.now();
        Integer longest = roomExamRepository.findLongestDurationMinutes(roomId);
        RoomPhase phase = room.phaseAt(now, longest);
        LocalDateTime end = room.endAt(longest);
        boolean isOwner = room.isOwnedBy(viewer.getUserId());

        if (!isOwner) {
            Optional<RoomMember> mine = memberRepository.findById_RoomIdAndId_UserId(roomId, viewer.getUserId());
            if (mine.isEmpty() || mine.get().getStatus() == MemberStatus.KICKED) {
                // 404 như mọi chỗ khác của phòng: người ngoài không biết phòng có tồn tại.
                throw new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId);
            }
            if (phase != RoomPhase.ENDED) {
                throw new BusinessException("Bảng xếp hạng mở khi phòng hết giờ làm bài"
                        + (end != null ? " (lúc " + end + ")." : "."));
            }
        }

        List<RoomMember> members = memberRepository.findSeatedMembers(roomId);
        Map<String, RoomMember> memberById = new LinkedHashMap<>();
        for (RoomMember m : members) {
            memberById.put(m.getUser().getUserId(), m);
        }

        List<Board> boards = new ArrayList<>();
        for (RoomExam re : roomExamRepository.findWithExamByRoomId(roomId)) {
            Exam exam = re.getExam();
            // Chỉ tính lượt làm TRONG buổi thi của phòng: cùng một đề có thể đã được làm ở phòng khác hoặc trước giờ bắt đầu.
            boolean notStarted = phase == RoomPhase.DRAFT || phase == RoomPhase.WAITING;
            LocalDateTime from = room.getStartTime();
            List<ExamSubmission> inWindow = memberById.isEmpty() || notStarted
                    ? List.of()
                    : submissionRepository.findByExam_ExamIdAndStudent_UserIdIn(
                                    exam.getExamId(), memberById.keySet()).stream()
                            .filter(s -> s.getStartedAt() != null
                                    && (from == null || !s.getStartedAt().isBefore(from))
                                    && (from == null || end == null || !s.getStartedAt().isAfter(end)))
                            .toList();
            boards.add(roomBoard(exam, members, inWindow, viewer, isOwner));
        }

        return LeaderboardResponse.builder()
                .scope(LeaderboardResponse.Scope.ROOM)
                .roomId(roomId)
                .roomName(room.getName())
                .phase(phase)
                .startTime(room.getStartTime())
                .endTime(end)
                .finalResults(phase == RoomPhase.ENDED)
                .boards(boards)
                .serverTime(now)
                .build();
    }

    private Board roomBoard(Exam exam, List<RoomMember> members, List<ExamSubmission> submissions,
                            User viewer, boolean isOwner) {
        Map<String, List<ExamSubmission>> byStudent = submissions.stream()
                .collect(Collectors.groupingBy(s -> s.getStudent().getUserId()));

        // Mỗi thí sinh một bài đại diện: lượt tốt nhất đã nộp.
        Map<String, ExamSubmission> best = new HashMap<>();
        Map<String, ExamSubmission> running = new HashMap<>();
        for (Map.Entry<String, List<ExamSubmission>> e : byStudent.entrySet()) {
            e.getValue().stream().filter(s -> !s.isInProgress()).min(BEST_FIRST)
                    .ifPresent(s -> best.put(e.getKey(), s));
            e.getValue().stream().filter(ExamSubmission::isInProgress).findFirst()
                    .ifPresent(s -> running.put(e.getKey(), s));
        }

        ExamFacts facts = factsOf(exam);
        Map<Integer, Long> correct = correctCounts(best.values());

        List<ExamSubmission> ranked = best.values().stream().sorted(BEST_FIRST).toList();
        Map<Integer, Integer> rankOf = rankOf(ranked);

        List<Row> rows = new ArrayList<>(members.size());
        Row myRow = null;
        for (RoomMember m : members) {
            String userId = m.getUser().getUserId();
            boolean me = userId.equals(viewer.getUserId());
            ExamSubmission s = best.get(userId);
            Row row;
            if (s != null) {
                row = rowOf(s, facts, correct, rankOf, m.getUser().getFullName(), m.getSeatNo(),
                        me, isOwner || me);
            } else {
                row = Row.builder()
                        .fullName(m.getUser().getFullName())
                        .seatNo(m.getSeatNo())
                        .me(me)
                        .status(running.containsKey(userId) ? "IN_PROGRESS" : "NOT_STARTED")
                        .build();
            }
            rows.add(row);
            if (me) {
                myRow = row;
            }
        }
        // Có hạng trước (theo hạng), chưa có bài xuống cuối (theo số ghế).
        rows.sort(Comparator.comparing(Row::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Row::getSeatNo, Comparator.nullsLast(Comparator.naturalOrder())));

        return board(exam, facts, members.size(), ranked, rows, myRow);
    }

    // ── Đề tự do ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public LeaderboardResponse examLeaderboard(String viewerEmail, Integer examId) {
        User viewer = requireUser(viewerEmail);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        if (!Boolean.TRUE.equals(exam.getIsPublic())) {
            // Đề chỉ nằm trong phòng thì xếp hạng theo phòng — xem roomLeaderboard.
            throw new ResourceNotFoundException("Đề này không có bảng xếp hạng chung. "
                    + "Xem bảng xếp hạng trong phòng thi của bạn.");
        }

        Map<String, ExamSubmission> best = new HashMap<>();
        for (ExamSubmission s : submissionRepository.findFinishedWithStudentByExamId(examId)) {
            best.merge(s.getStudent().getUserId(), s,
                    (a, b) -> BEST_FIRST.compare(a, b) <= 0 ? a : b);
        }
        List<ExamSubmission> ranked = best.values().stream().sorted(BEST_FIRST).toList();
        Map<Integer, Integer> rankOf = rankOf(ranked);

        ExamSubmission mine = best.get(viewer.getUserId());
        List<ExamSubmission> top = ranked.subList(0, Math.min(EXAM_TOP_N, ranked.size()));
        List<ExamSubmission> needCounts = new ArrayList<>(top);
        if (mine != null && !top.contains(mine)) {
            needCounts.add(mine);
        }

        ExamFacts facts = factsOf(exam);
        Map<Integer, Long> correct = correctCounts(needCounts);

        List<Row> rows = new ArrayList<>(top.size());
        for (ExamSubmission s : top) {
            boolean me = s == mine;
            rows.add(rowOf(s, facts, correct, rankOf, s.getStudent().getFullName(), null, me, me));
        }
        // Mình nằm ngoài tốp thì chỉ trả qua myRow — client hiện nó tách riêng
        // dưới tốp, kèm hạng thật (vd. "hạng 57/203").
        Row myRow = mine == null ? null : rows.stream().filter(Row::isMe).findFirst()
                .orElseGet(() -> rowOf(mine, facts, correct, rankOf,
                        mine.getStudent().getFullName(), null, true, true));

        return LeaderboardResponse.builder()
                .scope(LeaderboardResponse.Scope.EXAM)
                .finalResults(true)
                .boards(List.of(board(exam, facts, ranked.size(), ranked, rows, myRow)))
                .serverTime(LocalDateTime.now())
                .build();
    }

    // ── Dựng bảng ───────────────────────────────────────────────────────────

    /** Những con số của đề mà mọi dòng đều cần: điểm tối đa và số câu. */
    private record ExamFacts(BigDecimal maxScore, int totalQuestions) {
    }

    private ExamFacts factsOf(Exam exam) {
        List<ExamQuestion> questions =
                examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId());
        BigDecimal max = BigDecimal.ZERO;
        for (ExamQuestion q : questions) {
            max = max.add(q.getPoints() != null ? q.getPoints() : DEFAULT_POINTS);
        }
        return new ExamFacts(max, questions.size());
    }

    private Map<Integer, Long> correctCounts(java.util.Collection<ExamSubmission> submissions) {
        Map<Integer, Long> result = new HashMap<>();
        if (submissions.isEmpty()) {
            return result;
        }
        List<Integer> ids = submissions.stream().map(ExamSubmission::getSubmissionId).toList();
        for (Object[] row : detailRepository.countCorrectBySubmissionIdIn(ids)) {
            result.put((Integer) row[0], (Long) row[1]);
        }
        return result;
    }

    /** Hạng theo kiểu thi đấu: bằng điểm và bằng thời gian thì cùng hạng, người kế tiếp nhảy hạng (1, 2, 2, 4) */
    private Map<Integer, Integer> rankOf(List<ExamSubmission> ranked) {
        Map<Integer, Integer> ranks = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            ExamSubmission s = ranked.get(i);
            int rank = i + 1;
            if (i > 0 && BEST_FIRST.compare(ranked.get(i - 1), s) == 0) {
                rank = ranks.get(ranked.get(i - 1).getSubmissionId());
            }
            ranks.put(s.getSubmissionId(), rank);
        }
        return ranks;
    }

    private Row rowOf(ExamSubmission s, ExamFacts facts, Map<Integer, Long> correct,
                      Map<Integer, Integer> rankOf, String fullName, Integer seatNo,
                      boolean me, boolean revealSubmissionId) {
        BigDecimal score = s.getTotalScore() == null ? BigDecimal.ZERO : s.getTotalScore();
        long duration = durationSeconds(s);
        return Row.builder()
                .rank(rankOf.get(s.getSubmissionId()))
                .fullName(fullName)
                .seatNo(seatNo)
                .me(me)
                .status(s.getStatus().name())
                .score(score)
                .percent(percentOf(score, facts.maxScore()))
                .correctAnswers(correct.getOrDefault(s.getSubmissionId(), 0L).intValue())
                .durationSeconds(duration == Long.MAX_VALUE ? null : duration)
                .submittedAt(s.getSubmittedAt())
                .autoSubmitted(Boolean.TRUE.equals(s.getAutoSubmitted()))
                .awaitingManualGrading(s.getStatus() == SubmissionStatus.SUBMITTED)
                .submissionId(revealSubmissionId ? s.getSubmissionId() : null)
                .build();
    }

    private Board board(Exam exam, ExamFacts facts, int participants, List<ExamSubmission> ranked,
                        List<Row> rows, Row myRow) {
        BigDecimal highest = ranked.isEmpty() ? null : ranked.get(0).getTotalScore();
        BigDecimal average = null;
        if (!ranked.isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (ExamSubmission s : ranked) {
                sum = sum.add(s.getTotalScore() == null ? BigDecimal.ZERO : s.getTotalScore());
            }
            average = sum.divide(BigDecimal.valueOf(ranked.size()), 2, RoundingMode.HALF_UP);
        }
        return Board.builder()
                .examId(exam.getExamId())
                .examTitle(exam.getTitle())
                .maxScore(facts.maxScore())
                .totalQuestions(facts.totalQuestions())
                .durationMinutes(exam.getDurationMinutes())
                .participants(participants)
                .submittedCount(ranked.size())
                .averageScore(average)
                .highestScore(highest)
                .rows(rows)
                .myRow(myRow)
                .build();
    }

    private static Double percentOf(BigDecimal score, BigDecimal max) {
        if (max == null || max.signum() == 0) {
            return null;
        }
        return score.multiply(BigDecimal.valueOf(100))
                .divide(max, 1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Thời gian làm bài thật: từ lúc bắt đầu tới lúc nộp (bài tự nộp thì tới hạn nộp). */
    private static long durationSeconds(ExamSubmission s) {
        LocalDateTime endAt = s.getSubmittedAt() != null ? s.getSubmittedAt() : s.getExpiresAt();
        if (s.getStartedAt() == null || endAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, Duration.between(s.getStartedAt(), endAt).getSeconds());
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }
}
