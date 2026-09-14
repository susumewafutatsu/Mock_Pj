package com.example.demo.service.impl;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.MemberStatus;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.RoomPhase;
import com.example.demo.domain.enums.RoomStatus;
import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.Room;
import com.example.demo.domain.model.RoomExam;
import com.example.demo.domain.model.RoomExamKey;
import com.example.demo.domain.model.RoomMember;
import com.example.demo.domain.model.RoomMemberKey;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.RoomCreateRequest;
import com.example.demo.dto.request.RoomDuplicateRequest;
import com.example.demo.dto.request.RoomJoinRequest;
import com.example.demo.dto.request.RoomUpdateRequest;
import com.example.demo.dto.response.RoomMemberResponse;
import com.example.demo.dto.response.RoomMonitorResponse;
import com.example.demo.dto.response.RoomResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.RoomExamRepository;
import com.example.demo.repository.RoomMemberRepository;
import com.example.demo.repository.RoomRepository;
import com.example.demo.repository.SubjectLevelRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.NotificationService;
import com.example.demo.service.RoomService;
import com.example.demo.service.SubmissionService;
import com.example.demo.util.DbTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Phòng thi: một buổi thi cho một đề. */
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomServiceImpl.class);

    /** Bảng chữ cái sinh mã phòng — bỏ 0/O/1/I/L cho dễ đọc. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Nhắc thí sinh trước giờ hẹn bao nhiêu phút. */
    private static final int REMINDER_MINUTES = 15;

    /** Bỏ qua thông báo đã trễ quá chừng này phút (phòng cũ, server tắt lâu). */
    private static final int NOTICE_GRACE_MINUTES = 10;

    private static final DateTimeFormatter HOUR_DAY = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private static final String ROOMS_LINK = "/student/exams?tab=rooms";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomExamRepository roomExamRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final SubjectLevelRepository levelRepository;
    private final UserRepository userRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final SubmissionDetailRepository detailRepository;
    private final SubmissionService submissionService;
    private final NotificationService notificationService;

    // ── Người ra đề ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(String ownerEmail) {
        User owner = requireUser(ownerEmail);
        return toResponses(roomRepository.findByOwner(owner.getUserId()), owner, true);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getRoom(String viewerEmail, Integer roomId) {
        User viewer = requireUser(viewerEmail);
        Room room = requireRoom(roomId);
        boolean member = memberRepository.existsById_RoomIdAndId_UserIdAndStatus(
                roomId, viewer.getUserId(), MemberStatus.ACTIVE);
        if (!room.isOwnedBy(viewer.getUserId()) && !member) {
            throw new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId);
        }
        return describe(room, viewer, true);
    }

    @Override
    @Transactional
    public RoomResponse createRoom(String ownerEmail, RoomCreateRequest request) {
        User owner = requireTeacher(ownerEmail);
        Exam exam = request.getExamId() == null ? null : requireUsableExam(request.getExamId(), owner);
        if (Boolean.TRUE.equals(request.getOpenLobby()) && exam == null) {
            throw new BusinessException("Chọn đề thi trước khi mở sảnh chờ");
        }

        SubjectLevel level = request.getLevelId() != null
                ? requireLevel(request.getLevelId())
                : exam == null ? null : exam.getLevel();

        Room room = Room.builder()
                .name(request.getName().trim())
                .code(generateUniqueCode())
                .owner(owner)
                .level(level)
                .capacity(request.getCapacity())
                .joinPolicy(request.getJoinPolicy() == null ? JoinPolicy.CODE : request.getJoinPolicy())
                .status(RoomStatus.DRAFT)
                .lateJoinMinutes(request.getLateJoinMinutes() == null
                        ? Room.DEFAULT_LATE_JOIN_MINUTES : request.getLateJoinMinutes())
                .instructions(trimToNull(request.getInstructions()))
                .build();
        if (request.getStartTime() != null) {
            requireFuture(request.getStartTime());
            room.setStartTime(DbTime.atSecond(request.getStartTime()));
        }
        roomRepository.save(room);

        if (exam != null) {
            saveRoomExam(room, exam);
            if (Boolean.TRUE.equals(request.getOpenLobby())) {
                room.setStatus(RoomStatus.OPEN);
                roomRepository.save(room);
            }
        }
        log.info("Mở phòng thi roomId={} code={} owner={}", room.getRoomId(), room.getCode(), owner.getUserId());
        return describe(room, owner, true);
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(String ownerEmail, Integer roomId, RoomUpdateRequest request) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);
        LocalDateTime now = LocalDateTime.now();

        if (request.getName() != null && !request.getName().isBlank()) {
            room.setName(request.getName().trim());
        }
        if (request.getLevelId() != null) {
            room.setLevel(requireLevel(request.getLevelId()));
        }
        if (request.getCapacity() != null) {
            long active = memberRepository.countActive(roomId);
            if (request.getCapacity() < active) {
                throw new BusinessException("Phòng đang có " + active
                        + " người, không thể hạ sức chứa xuống " + request.getCapacity());
            }
            room.setCapacity(request.getCapacity());
        }
        if (request.getJoinPolicy() != null) {
            room.setJoinPolicy(request.getJoinPolicy());
        }
        if (request.getLateJoinMinutes() != null) {
            room.setLateJoinMinutes(request.getLateJoinMinutes());
        }
        if (request.getInstructions() != null) {
            room.setInstructions(trimToNull(request.getInstructions()));
        }
        if (request.getExamId() != null) {
            Exam exam = requireUsableExam(request.getExamId(), owner);
            requireExamsEditable(room);
            replaceExam(room, exam);
            if (room.getLevel() == null) {
                room.setLevel(exam.getLevel());
            }
        }

        if (request.getStatus() == RoomStatus.RUNNING) {
            roomRepository.save(room);
            return startExam(ownerEmail, roomId);
        }
        if (request.getStatus() == RoomStatus.CLOSED) {
            roomRepository.save(room);
            return endExam(ownerEmail, roomId);
        }
        if (request.getStatus() != null) {
            // Buổi thi đã diễn ra thì không mở lại — bảng xếp hạng gắn với khung giờ đó.
            if (room.hasStarted(now) || room.getStatus() == RoomStatus.RUNNING) {
                throw new BusinessException("Buổi thi của phòng này đã diễn ra, không mở lại được. "
                        + "Dùng \"Nhân bản\" để tạo buổi thi mới.");
            }
            if (request.getStatus() == RoomStatus.OPEN && roomExamRepository.countById_RoomId(roomId) == 0) {
                throw new BusinessException("Phòng chưa có đề thi, chưa mở sảnh chờ được");
            }
            room.setStatus(request.getStatus());
        }

        boolean scheduling = request.getStartTime() != null || Boolean.TRUE.equals(request.getClearStartTime());
        if (scheduling) {
            RoomPhase phase = room.phaseAt(now, examMinutes(roomId));
            if (phase == RoomPhase.IN_PROGRESS || phase == RoomPhase.ENDED) {
                throw new BusinessException("Phòng đã bắt đầu làm bài, không đổi được giờ hẹn nữa");
            }
            if (Boolean.TRUE.equals(request.getClearStartTime())) {
                room.setStartTime(null);
            } else {
                requireFuture(request.getStartTime());
                room.setStartTime(DbTime.atSecond(request.getStartTime()));
            }
            room.setEndTime(null);
            room.setReminderSentAt(null);
            room.setStartNotifiedAt(null);
        }
        if (request.getEndTime() != null) {
            room.setEndTime(request.getEndTime());
        }

        roomRepository.save(room);
        return describe(room, owner, true);
    }

    @Override
    @Transactional
    public RoomResponse startExam(String ownerEmail, Integer roomId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);
        LocalDateTime now = DbTime.now();
        Integer minutes = examMinutes(roomId);

        if (minutes == null) {
            throw new BusinessException("Phòng chưa có đề thi, chưa bắt đầu được");
        }
        switch (room.phaseAt(now, minutes)) {
            case DRAFT -> throw new BusinessException("Phòng còn là nháp. Mở sảnh chờ trước rồi mới bắt đầu làm bài.");
            case IN_PROGRESS -> throw new BusinessException("Phòng đang trong giờ làm bài rồi");
            case ENDED -> throw new BusinessException("Phòng thi đã kết thúc");
            default -> { /* WAITING */ }
        }

        room.setStatus(RoomStatus.RUNNING);
        room.setStartTime(now);
        room.setEndTime(now.plusMinutes(minutes));
        room.setStartNotifiedAt(now);
        if (room.getReminderSentAt() == null) {
            room.setReminderSentAt(now);
        }
        roomRepository.save(room);
        log.info("Bắt đầu làm bài roomId={} lúc {} tới {}", roomId, now, room.getEndTime());

        notificationService.notifyAll(membersOf(roomId), NotificationService.Kind.ROOM_STARTED,
                "Phòng \"" + room.getName() + "\" đã bắt đầu làm bài",
                "Vào phòng để làm bài — hết giờ lúc " + room.getEndTime().format(HOUR_DAY) + ".",
                ROOMS_LINK);
        return describe(room, owner, true);
    }

    @Override
    @Transactional
    public RoomResponse endExam(String ownerEmail, Integer roomId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);
        LocalDateTime now = DbTime.now();
        RoomPhase phase = room.phaseAt(now, examMinutes(roomId));

        if (phase == RoomPhase.ENDED && room.getStatus() == RoomStatus.CLOSED) {
            return describe(room, owner, true);
        }
        if (phase == RoomPhase.IN_PROGRESS) {
            room.setEndTime(now);
        }
        boolean notify = room.getEndNotifiedAt() == null && phase != RoomPhase.DRAFT;
        room.setStatus(RoomStatus.CLOSED);
        room.setEndNotifiedAt(now);
        roomRepository.save(room);

        if (phase == RoomPhase.IN_PROGRESS) {
            int cut = submissionRepository.cutRunningSessionsOfRoom(roomId, now);
            if (cut > 0) {
                submissionService.autoSubmitExpiredSessions();
            }
            log.info("Kết thúc sớm roomId={}, thu {} bài đang làm", roomId, cut);
        }

        Room fresh = roomRepository.findById(roomId).orElseThrow();
        if (notify) {
            boolean ran = phase == RoomPhase.IN_PROGRESS || phase == RoomPhase.ENDED;
            notificationService.notifyAll(membersOf(roomId), NotificationService.Kind.ROOM_ENDED,
                    ran ? "Phòng \"" + fresh.getName() + "\" đã kết thúc"
                        : "Phòng \"" + fresh.getName() + "\" đã đóng",
                    ran ? "Bảng xếp hạng và kết quả của cả phòng đã có."
                        : "Người ra đề đã đóng phòng trước giờ thi.",
                    ROOMS_LINK);
        }
        return describe(fresh, owner, true);
    }

    @Override
    @Transactional
    public RoomResponse duplicateRoom(String ownerEmail, Integer roomId, RoomDuplicateRequest request) {
        User owner = requireTeacher(ownerEmail);
        Room source = requireOwnedRoom(roomId, owner);
        RoomDuplicateRequest req = request == null ? new RoomDuplicateRequest() : request;

        String name = req.getName() != null && !req.getName().isBlank()
                ? req.getName().trim()
                : truncate(source.getName() + " (buổi mới)", 100);

        Room copy = Room.builder()
                .name(name)
                .code(generateUniqueCode())
                .owner(owner)
                .level(source.getLevel())
                .capacity(source.getCapacity())
                .joinPolicy(source.getJoinPolicy())
                .status(RoomStatus.DRAFT)
                .lateJoinMinutes(source.getLateJoinMinutes())
                .instructions(source.getInstructions())
                .build();
        if (req.getStartTime() != null) {
            requireFuture(req.getStartTime());
            copy.setStartTime(DbTime.atSecond(req.getStartTime()));
        }
        roomRepository.save(copy);

        roomExamRepository.findWithExamByRoomId(roomId).stream().findFirst()
                .ifPresent(re -> saveRoomExam(copy, re.getExam()));

        if (Boolean.TRUE.equals(req.getKeepMembers())) {
            List<RoomMember> members = memberRepository.findActiveMembers(roomId);
            int seat = 1;
            List<User> added = new ArrayList<>();
            for (RoomMember m : members) {
                memberRepository.save(RoomMember.builder()
                        .id(new RoomMemberKey(copy.getRoomId(), m.getUser().getUserId()))
                        .room(copy)
                        .user(m.getUser())
                        .seatNo(seat++)
                        .status(MemberStatus.ACTIVE)
                        .build());
                added.add(m.getUser());
            }
            if (copy.getCapacity() != null && added.size() > copy.getCapacity()) {
                copy.setCapacity(added.size());
                roomRepository.save(copy);
            }
            notificationService.notifyAll(added, NotificationService.Kind.ROOM_ADDED,
                    "Bạn được thêm vào phòng \"" + copy.getName() + "\"",
                    "Người ra đề đã tạo buổi thi mới và giữ bạn trong danh sách.", ROOMS_LINK);
        }
        log.info("Nhân bản phòng roomId={} → {}", roomId, copy.getRoomId());
        return describe(copy, owner, true);
    }

    @Override
    @Transactional
    public void deleteRoom(String ownerEmail, Integer roomId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);
        if (memberRepository.countSeatsIssued(roomId) > 0) {
            throw new BusinessException("Phòng đã có người tham gia. Hãy đóng phòng thay vì xoá, "
                    + "để giữ lại lịch sử làm bài.");
        }
        roomRepository.delete(room);
        log.info("Xoá phòng thi roomId={} owner={}", roomId, owner.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getMembers(String ownerEmail, Integer roomId) {
        User owner = requireUser(ownerEmail);
        requireOwnedRoom(roomId, owner);
        return memberRepository.findActiveMembers(roomId).stream()
                .map(m -> RoomMemberResponse.builder()
                        .userId(m.getUser().getUserId())
                        .fullName(m.getUser().getFullName())
                        .email(m.getUser().getEmail())
                        .seatNo(m.getSeatNo())
                        .status(m.getStatus())
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoomMonitorResponse monitor(String ownerEmail, Integer roomId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);
        LocalDateTime now = LocalDateTime.now();
        Integer minutes = examMinutes(roomId);
        RoomPhase phase = room.phaseAt(now, minutes);
        Exam exam = roomExamRepository.findWithExamByRoomId(roomId).stream()
                .findFirst().map(RoomExam::getExam).orElse(null);

        List<RoomMember> members = memberRepository.findActiveMembers(roomId);
        Map<String, ExamSubmission> sessionOf = new HashMap<>();
        Map<Integer, Long> answeredOf = new HashMap<>();
        int totalQuestions = 0;
        BigDecimal maxScore = BigDecimal.ZERO;

        boolean started = phase == RoomPhase.IN_PROGRESS || phase == RoomPhase.ENDED;
        if (exam != null) {
            List<ExamQuestion> questions = examQuestionRepository.findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId());
            totalQuestions = questions.size();
            maxScore = questions.stream()
                    .map(q -> q.getPoints() == null ? BigDecimal.ONE : q.getPoints())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (started && !members.isEmpty()) {
                Set<String> ids = members.stream().map(m -> m.getUser().getUserId()).collect(Collectors.toSet());
                LocalDateTime from = room.getStartTime();
                LocalDateTime end = room.endAt(minutes);
                for (ExamSubmission s : submissionRepository.findByExam_ExamIdAndStudent_UserIdIn(exam.getExamId(), ids)) {
                    if (s.getStartedAt() == null
                            || (from != null && s.getStartedAt().isBefore(from))
                            || (end != null && s.getStartedAt().isAfter(end))) {
                        continue;
                    }
                    sessionOf.merge(s.getStudent().getUserId(), s,
                            (a, b) -> a.getAttemptNumber() >= b.getAttemptNumber() ? a : b);
                }
                if (!sessionOf.isEmpty()) {
                    for (Object[] row : detailRepository.countAnsweredBySubmissionIdIn(
                            sessionOf.values().stream().map(ExamSubmission::getSubmissionId).toList())) {
                        answeredOf.put((Integer) row[0], ((Number) row[1]).longValue());
                    }
                }
            }
        }

        List<RoomMonitorResponse.Row> rows = new ArrayList<>();
        int online = 0, notStarted = 0, inProgress = 0, submitted = 0, atRisk = 0;
        for (RoomMember m : members) {
            ExamSubmission s = sessionOf.get(m.getUser().getUserId());
            String status = s == null ? "NOT_STARTED" : s.isInProgress() ? "IN_PROGRESS" : "SUBMITTED";
            boolean risky = s != null && s.isInProgress() && Boolean.TRUE.equals(s.getAtRiskStatus());
            boolean isOnline = m.isOnlineAt(now) || (s != null && s.isInProgress() && !risky);

            switch (status) {
                case "IN_PROGRESS" -> inProgress++;
                case "SUBMITTED" -> submitted++;
                default -> notStarted++;
            }
            if (isOnline) online++;
            if (risky) atRisk++;

            RoomMonitorResponse.Row.RowBuilder row = RoomMonitorResponse.Row.builder()
                    .userId(m.getUser().getUserId())
                    .fullName(m.getUser().getFullName())
                    .email(m.getUser().getEmail())
                    .seatNo(m.getSeatNo())
                    .joinedAt(m.getJoinedAt())
                    .online(isOnline)
                    .lastSeenAt(m.getLastSeenAt())
                    .status(status)
                    .atRisk(risky);
            if (s != null) {
                row.answered(answeredOf.getOrDefault(s.getSubmissionId(), 0L).intValue())
                        .startedAt(s.getStartedAt())
                        .submittedAt(s.getSubmittedAt())
                        .autoSubmitted(Boolean.TRUE.equals(s.getAutoSubmitted()))
                        .submissionId(s.getSubmissionId());
                if (!s.isInProgress()) {
                    row.score(s.getTotalScore())
                            .percent(maxScore.signum() == 0 || s.getTotalScore() == null ? null
                                    : s.getTotalScore().multiply(BigDecimal.valueOf(100))
                                            .divide(maxScore, 0, RoundingMode.HALF_UP).intValue());
                }
            }
            rows.add(row.build());
        }
        rows.sort(Comparator.comparing(RoomMonitorResponse.Row::getSeatNo));

        return RoomMonitorResponse.builder()
                .roomId(roomId)
                .phase(phase)
                .startTime(room.getStartTime())
                .endTime(room.endAt(minutes))
                .lateJoinUntil(started ? room.lateJoinUntil(minutes) : null)
                .serverTime(now)
                .examId(exam == null ? null : exam.getExamId())
                .examTitle(exam == null ? null : exam.getTitle())
                .totalQuestions(totalQuestions)
                .maxScore(maxScore)
                .joined(members.size())
                .online(online)
                .notStarted(notStarted)
                .inProgress(inProgress)
                .submitted(submitted)
                .atRisk(atRisk)
                .members(rows)
                .build();
    }

    @Override
    @Transactional
    public void kickMember(String ownerEmail, Integer roomId, String userId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);

        RoomMember member = memberRepository.findById_RoomIdAndId_UserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người này không có trong phòng"));
        if (!member.isActive()) {
            throw new BusinessException("Người này đã không còn trong phòng");
        }
        LocalDateTime now = DbTime.now();
        member.deactivate(MemberStatus.KICKED, now);
        memberRepository.save(member);

        // Mời ra giữa giờ thi: thu bài đang làm ngay.
        if (room.phaseAt(now, examMinutes(roomId)) == RoomPhase.IN_PROGRESS) {
            boolean cut = false;
            for (RoomExam re : roomExamRepository.findById_RoomIdOrderByOrderNoAsc(roomId)) {
                Optional<ExamSubmission> running = submissionRepository
                        .findByExam_ExamIdAndStudent_UserIdAndStatus(re.getId().getExamId(), userId, SubmissionStatus.IN_PROGRESS);
                if (running.isPresent()) {
                    running.get().setExpiresAt(now);
                    submissionRepository.save(running.get());
                    cut = true;
                }
            }
            if (cut) {
                submissionRepository.flush();
                submissionService.autoSubmitExpiredSessions();
            }
        }
        notificationService.notify(member.getUser(), NotificationService.Kind.ROOM_KICKED,
                "Bạn đã được mời ra khỏi phòng \"" + room.getName() + "\"",
                "Liên hệ người ra đề nếu có nhầm lẫn.", ROOMS_LINK);
        log.info("Mời ra khỏi phòng roomId={} userId={}", roomId, userId);
    }

    @Override
    @Transactional
    public RoomResponse attachExam(String ownerEmail, Integer roomId, Integer examId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);
        Exam exam = requireUsableExam(examId, owner);
        if (roomExamRepository.existsById_RoomIdAndId_ExamId(roomId, examId)) {
            throw new BusinessException("Đề thi này đã có trong phòng");
        }
        requireExamsEditable(room);
        if (roomExamRepository.countById_RoomId(roomId) > 0) {
            throw new BusinessException("Mỗi phòng thi dùng một đề. Muốn đổi đề thì sửa phòng.");
        }
        saveRoomExam(room, exam);
        return describe(room, owner, true);
    }

    @Override
    @Transactional
    public RoomResponse detachExam(String ownerEmail, Integer roomId, Integer examId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);
        RoomExamKey key = new RoomExamKey(roomId, examId);
        if (!roomExamRepository.existsById(key)) {
            throw new ResourceNotFoundException("Đề thi này không có trong phòng");
        }
        requireExamsEditable(room);
        if (room.getStatus() == RoomStatus.OPEN) {
            throw new BusinessException("Sảnh chờ đang mở, không gỡ đề được. Đổi đề trong phần Sửa phòng.");
        }
        roomExamRepository.deleteById(key);
        return describe(room, owner, true);
    }

    // ── Thí sinh ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getJoinedRooms(String userEmail) {
        User user = requireUser(userEmail);
        List<Integer> roomIds = memberRepository.findActiveRoomIdsByUserId(user.getUserId());
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return toResponses(roomRepository.findAllByIdWithDetails(roomIds), user, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> browseOpenRooms(String userEmail) {
        User user = requireUser(userEmail);
        List<Room> rooms = new ArrayList<>(roomRepository.findOpenRooms(RoomStatus.OPEN));
        rooms.addAll(roomRepository.findOpenRooms(RoomStatus.RUNNING));
        return toResponses(rooms, user, true).stream()
                .filter(RoomResponse::isAcceptingMembers)
                .toList();
    }

    @Override
    @Transactional
    public RoomResponse join(String userEmail, RoomJoinRequest request) {
        User user = requireStudent(userEmail);
        String code = request.getCode() == null ? "" : request.getCode().trim().toUpperCase();
        Room found = roomRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không có phòng nào mang mã này"));
        return joinRoom(user, found);
    }

    @Override
    @Transactional
    public RoomResponse joinOpenRoom(String userEmail, Integer roomId) {
        User user = requireStudent(userEmail);
        Room room = requireRoom(roomId);
        if (room.getJoinPolicy() != JoinPolicy.OPEN) {
            throw new ResourceNotFoundException("Phòng này cần mã để vào");
        }
        return joinRoom(user, room);
    }

    @Override
    @Transactional
    public void leave(String userEmail, Integer roomId) {
        User user = requireUser(userEmail);
        RoomMember member = memberRepository.findById_RoomIdAndId_UserId(roomId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn không ở trong phòng này"));
        if (!member.isActive()) {
            return;
        }
        for (RoomExam re : roomExamRepository.findById_RoomIdOrderByOrderNoAsc(roomId)) {
            if (submissionRepository.findByExam_ExamIdAndStudent_UserIdAndStatus(
                    re.getId().getExamId(), user.getUserId(), SubmissionStatus.IN_PROGRESS).isPresent()) {
                throw new BusinessException("Bạn đang làm bài trong phòng này. Nộp bài trước khi rời phòng.");
            }
        }
        member.deactivate(MemberStatus.LEFT, DbTime.now());
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public void presence(String userEmail, Integer roomId) {
        User user = requireUser(userEmail);
        RoomMember member = memberRepository.findById_RoomIdAndId_UserId(roomId, user.getUserId())
                .filter(RoomMember::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId));
        member.setLastSeenAt(DbTime.now());
        memberRepository.save(member);
    }

    // ── Job nền ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public int notifyScheduledRooms() {
        LocalDateTime now = DbTime.now();
        LocalDateTime grace = now.minusMinutes(NOTICE_GRACE_MINUTES);
        int sent = 0;
        for (Room room : roomRepository.findScheduledNeedingNotice(List.of(RoomStatus.OPEN, RoomStatus.RUNNING))) {
            Integer minutes = examMinutes(room.getRoomId());
            RoomPhase phase = room.phaseAt(now, minutes);
            LocalDateTime start = room.getStartTime();

            if (phase == RoomPhase.WAITING && room.getReminderSentAt() == null
                    && !start.isAfter(now.plusMinutes(REMINDER_MINUTES))) {
                notificationService.notifyAll(membersOf(room.getRoomId()), NotificationService.Kind.ROOM_REMINDER,
                        "Phòng \"" + room.getName() + "\" sắp bắt đầu",
                        "Buổi thi bắt đầu lúc " + start.format(HOUR_DAY) + ". Vào sảnh chờ trước vài phút.",
                        ROOMS_LINK);
                room.setReminderSentAt(now);
                sent++;
            }
            if (phase == RoomPhase.IN_PROGRESS && room.getStartNotifiedAt() == null) {
                if (!start.isBefore(grace)) {
                    LocalDateTime end = room.endAt(minutes);
                    notificationService.notifyAll(membersOf(room.getRoomId()), NotificationService.Kind.ROOM_STARTED,
                            "Phòng \"" + room.getName() + "\" đã bắt đầu làm bài",
                            "Vào phòng để làm bài" + (end == null ? "." : " — hết giờ lúc " + end.format(HOUR_DAY) + "."),
                            ROOMS_LINK);
                    sent++;
                }
                room.setStartNotifiedAt(now);
                if (room.getReminderSentAt() == null) {
                    room.setReminderSentAt(now);
                }
            }
            if (phase == RoomPhase.ENDED && room.getEndNotifiedAt() == null) {
                LocalDateTime end = room.endAt(minutes);
                if (end != null && !end.isBefore(grace)) {
                    notificationService.notifyAll(membersOf(room.getRoomId()), NotificationService.Kind.ROOM_ENDED,
                            "Phòng \"" + room.getName() + "\" đã kết thúc",
                            "Bảng xếp hạng và kết quả của cả phòng đã có.", ROOMS_LINK);
                    sent++;
                }
                room.setEndNotifiedAt(now);
                if (room.getStartNotifiedAt() == null) room.setStartNotifiedAt(now);
                if (room.getReminderSentAt() == null) room.setReminderSentAt(now);
            }
            roomRepository.save(room);
        }
        return sent;
    }

    // ── Hỗ trợ ─────────────────────────────────────────────────────────────

    private RoomResponse joinRoom(User user, Room found) {
        Room room = roomRepository.findByIdForUpdate(found.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng"));
        LocalDateTime now = DbTime.now();

        Optional<RoomMember> existing = memberRepository.findById_RoomIdAndId_UserId(room.getRoomId(), user.getUserId());
        if (existing.isPresent()) {
            RoomMember member = existing.get();
            if (member.getStatus() == MemberStatus.KICKED) {
                throw new UnauthorizedException("Bạn đã bị mời ra khỏi phòng này");
            }
            if (!member.isActive()) {
                requireAcceptingMembers(room);
                member.setStatus(MemberStatus.ACTIVE);
                member.setLeftAt(null);
            }
            member.setLastSeenAt(now);
            memberRepository.save(member);
            return describe(room, user, true);
        }

        requireAcceptingMembers(room);
        int lastSeat = memberRepository.findMaxSeatForUpdate(room.getRoomId());
        if (!room.hasRoomFor(memberRepository.countActive(room.getRoomId()))) {
            throw new BusinessException("Phòng đã đủ " + room.getCapacity() + " người");
        }

        RoomMember member = RoomMember.builder()
                .id(new RoomMemberKey(room.getRoomId(), user.getUserId()))
                .room(room)
                .user(user)
                .seatNo(lastSeat + 1)
                .status(MemberStatus.ACTIVE)
                .lastSeenAt(now)
                .build();
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            log.warn("Tranh ghế ở phòng roomId={}: {}", room.getRoomId(), e.getMessage());
            throw new BusinessException("Phòng vừa hết chỗ, có người vào trước bạn");
        }
        log.info("Vào phòng roomId={} userId={} ghế={}", room.getRoomId(), user.getUserId(), member.getSeatNo());
        return describe(room, user, true);
    }

    /** Phòng có nhận người mới không: sảnh chờ, hoặc trong khoảng cho vào muộn. */
    private void requireAcceptingMembers(Room room) {
        LocalDateTime now = LocalDateTime.now();
        Integer minutes = examMinutes(room.getRoomId());
        if (room.acceptsMembersAt(now, minutes)) {
            return;
        }
        throw new BusinessException(switch (room.phaseAt(now, minutes)) {
            case DRAFT -> "Phòng chưa mở";
            case IN_PROGRESS -> room.getLateJoinMinutes() == null || room.getLateJoinMinutes() == 0
                    ? "Phòng đã bắt đầu làm bài, không nhận thêm người"
                    : "Đã quá " + room.getLateJoinMinutes() + " phút đầu giờ thi, phòng không nhận thêm người";
            case ENDED -> "Phòng thi đã kết thúc";
            default -> "Phòng không nhận thêm người";
        });
    }

    /** Đề trong phòng chỉ đổi được trước giờ làm bài. */
    private void requireExamsEditable(Room room) {
        RoomPhase phase = room.phaseAt(LocalDateTime.now(), examMinutes(room.getRoomId()));
        if (phase == RoomPhase.IN_PROGRESS) {
            throw new BusinessException("Phòng đang trong giờ làm bài, không đổi đề được");
        }
        if (phase == RoomPhase.ENDED) {
            throw new BusinessException("Phòng thi đã kết thúc, đề được giữ nguyên để xem kết quả");
        }
    }

    /** Đề của chính người ra đề và đã có câu hỏi. */
    private Exam requireUsableExam(Integer examId, User owner) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        if (exam.getCreatedBy() == null || !exam.getCreatedBy().getUserId().equals(owner.getUserId())) {
            throw new UnauthorizedException("Chỉ dùng được đề thi do chính bạn tạo");
        }
        if (examQuestionRepository.countByExam_ExamId(examId) == 0) {
            throw new BusinessException("Đề \"" + exam.getTitle() + "\" chưa có câu hỏi nào");
        }
        return exam;
    }

    private void replaceExam(Room room, Exam exam) {
        List<RoomExam> current = roomExamRepository.findById_RoomIdOrderByOrderNoAsc(room.getRoomId());
        if (current.size() == 1 && current.get(0).getId().getExamId().equals(exam.getExamId())) {
            return;
        }
        roomExamRepository.deleteAll(current);
        roomExamRepository.flush();
        saveRoomExam(room, exam);
    }

    private void saveRoomExam(Room room, Exam exam) {
        roomExamRepository.save(RoomExam.builder()
                .id(new RoomExamKey(room.getRoomId(), exam.getExamId()))
                .room(room)
                .exam(exam)
                .orderNo(1)
                .build());
    }

    private Integer examMinutes(Integer roomId) {
        return roomExamRepository.findLongestDurationMinutes(roomId);
    }

    private Room requireRoom(Integer roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId));
    }

    private Room requireOwnedRoom(Integer roomId, User owner) {
        Room room = requireRoom(roomId);
        if (!room.isOwnedBy(owner.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId);
        }
        return room;
    }

    private SubjectLevel requireLevel(Integer levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trình độ id=" + levelId));
    }

    private void requireFuture(LocalDateTime startTime) {
        if (!startTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException("Giờ bắt đầu hẹn trước phải ở tương lai. "
                    + "Muốn thi ngay thì bấm \"Bắt đầu làm bài\".");
        }
    }

    private RoomResponse describe(Room room, User viewer, boolean revealCode) {
        return toResponses(List.of(room), viewer, revealCode).get(0);
    }

    private List<RoomResponse> toResponses(List<Room> rooms, User viewer, boolean revealCode) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Integer> roomIds = rooms.stream().map(Room::getRoomId).toList();
        LocalDateTime now = LocalDateTime.now();

        Map<Integer, Long> memberCounts = new HashMap<>();
        for (RoomMemberRepository.RoomHeadcount row : memberRepository.countActiveByRoomIdIn(roomIds)) {
            memberCounts.put(row.getRoomId(), row.getTotal());
        }
        Map<Integer, Long> onlineCounts = new HashMap<>();
        for (Object[] row : memberRepository.countOnlineByRoomIdIn(roomIds,
                now.minusSeconds(RoomMember.ONLINE_WINDOW_SECONDS))) {
            onlineCounts.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        Map<Integer, Long> examCounts = new HashMap<>();
        Map<Integer, Exam> examOf = new HashMap<>();
        for (RoomExam re : roomExamRepository.findWithExamByRoomIdIn(roomIds)) {
            examCounts.merge(re.getId().getRoomId(), 1L, Long::sum);
            examOf.putIfAbsent(re.getId().getRoomId(), re.getExam());
        }
        Map<Integer, Integer> longest = new HashMap<>();
        for (Object[] row : roomExamRepository.findLongestDurationByRoomIdIn(roomIds)) {
            longest.put((Integer) row[0], (Integer) row[1]);
        }

        List<RoomResponse> out = new ArrayList<>(rooms.size());
        for (Room room : rooms) {
            RoomMember mine = memberRepository.findById_RoomIdAndId_UserId(room.getRoomId(), viewer.getUserId())
                    .orElse(null);
            Exam exam = examOf.get(room.getRoomId());
            Integer minutes = longest.get(room.getRoomId());
            boolean isOwner = room.isOwnedBy(viewer.getUserId());
            boolean showCode = revealCode && (isOwner || (mine != null && mine.isActive()));
            long memberCount = memberCounts.getOrDefault(room.getRoomId(), 0L);
            RoomPhase phase = room.phaseAt(now, minutes);
            SubjectLevel level = room.getLevel();

            out.add(RoomResponse.builder()
                    .roomId(room.getRoomId())
                    .name(room.getName())
                    .code(showCode ? room.getCode() : null)
                    .ownerName(room.getOwner() == null ? null : room.getOwner().getFullName())
                    .levelId(level == null ? null : level.getLevelId())
                    .levelName(level == null ? null : level.getLevelName())
                    .subjectName(level == null || level.getSubject() == null ? null : level.getSubject().getSubjectName())
                    .capacity(room.getCapacity())
                    .memberCount(memberCount)
                    .seatsLeft(room.seatsLeft(memberCount))
                    .onlineCount(isOwner ? onlineCounts.getOrDefault(room.getRoomId(), 0L) : null)
                    .joinPolicy(room.getJoinPolicy())
                    .status(room.getStatus())
                    .phase(phase)
                    .startTime(room.getStartTime())
                    .endTime(room.endAt(minutes))
                    .durationMinutes(minutes)
                    .serverTime(now)
                    .examId(exam == null ? null : exam.getExamId())
                    .examTitle(exam == null ? null : exam.getTitle())
                    .examQuestionCount(exam == null ? null : (int) examQuestionRepository.countByExam_ExamId(exam.getExamId()))
                    .examCount(examCounts.getOrDefault(room.getRoomId(), 0L))
                    .instructions(room.getInstructions())
                    .lateJoinMinutes(room.getLateJoinMinutes())
                    .lateJoinUntil(room.getStartTime() == null ? null : room.lateJoinUntil(minutes))
                    .acceptingMembers(room.acceptsMembersAt(now, minutes))
                    .deletable(isOwner && memberRepository.countSeatsIssued(room.getRoomId()) == 0)
                    .owner(isOwner)
                    .myStatus(mine == null ? null : mine.getStatus())
                    .mySeatNo(mine == null ? null : mine.getSeatNo())
                    .build());
        }
        return out;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < CODE_MAX_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!roomRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new BusinessException("Không sinh được mã phòng, vui lòng thử lại");
    }

    private List<User> membersOf(Integer roomId) {
        return memberRepository.findActiveMembers(roomId).stream().map(RoomMember::getUser).toList();
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private User requireTeacher(String email) {
        User user = requireUser(email);
        if (user.getRole() != Role.TEACHER && user.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("Chỉ người ra đề mới mở được phòng thi");
        }
        return user;
    }

    private User requireStudent(String email) {
        User user = requireUser(email);
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ học viên mới vào được phòng thi");
        }
        return user;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
