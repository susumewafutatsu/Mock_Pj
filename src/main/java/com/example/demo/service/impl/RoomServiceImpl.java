package com.example.demo.service.impl;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.MemberStatus;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.RoomStatus;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.Room;
import com.example.demo.domain.model.RoomExam;
import com.example.demo.domain.model.RoomExamKey;
import com.example.demo.domain.model.RoomMember;
import com.example.demo.domain.model.RoomMemberKey;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.RoomCreateRequest;
import com.example.demo.dto.request.RoomJoinRequest;
import com.example.demo.dto.request.RoomUpdateRequest;
import com.example.demo.dto.response.RoomMemberResponse;
import com.example.demo.dto.response.RoomResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.RoomExamRepository;
import com.example.demo.repository.RoomMemberRepository;
import com.example.demo.repository.RoomRepository;
import com.example.demo.repository.SubjectLevelRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RoomService;
import com.example.demo.util.DbTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cài đặt phòng thi.
 *
 * Phần đáng đọc nhất ở đây là {@link #join}: cấp ghế cho một phòng có sức chứa
 * là một cuộc tranh chấp thật, và cách làm hiển nhiên nhất thì sai.
 */
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomServiceImpl.class);

    /**
     * Bảng chữ cái sinh mã phòng — cố ý bỏ 0/O/1/I/L.
     *
     * Mã này được đọc to cho cả phòng chép lại, nên mấy ký tự nhìn giống nhau
     * là nguồn gõ sai số một. Bỏ chúng đi rẻ hơn nhiều so với việc trả lời
     * "em gõ đúng mã rồi mà sao không vào được".
     */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 6;

    /** Số lần thử sinh mã trước khi chịu thua. Va chạm ở 31^6 tổ hợp là cực hiếm. */
    private static final int CODE_MAX_ATTEMPTS = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomExamRepository roomExamRepository;
    private final ExamRepository examRepository;
    private final SubjectLevelRepository levelRepository;
    private final UserRepository userRepository;

    // ── Phía người ra đề ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(String ownerEmail) {
        User owner = requireUser(ownerEmail);
        List<Room> rooms = roomRepository.findByOwner(owner.getUserId());
        return toResponses(rooms, owner, true);
    }

    @Override
    @Transactional
    public RoomResponse createRoom(String ownerEmail, RoomCreateRequest request) {
        User owner = requireTeacher(ownerEmail);

        SubjectLevel level = null;
        if (request.getLevelId() != null) {
            level = levelRepository.findById(request.getLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy trình độ id=" + request.getLevelId()));
        }

        Room room = Room.builder()
                .name(request.getName().trim())
                .code(generateUniqueCode())
                .owner(owner)
                .level(level)
                .capacity(request.getCapacity())
                .joinPolicy(request.getJoinPolicy() == null
                        ? JoinPolicy.CODE : request.getJoinPolicy())
                // Phòng mới luôn ở DRAFT: người ra đề cần gắn đề vào trước đã.
                // Mở sẵn thì thí sinh vào và thấy một phòng trống rỗng.
                .status(RoomStatus.DRAFT)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();

        roomRepository.save(room);
        log.info("Mở phòng thi roomId={} code={} owner={}",
                room.getRoomId(), room.getCode(), owner.getUserId());
        return toResponse(room, owner, true, 0L, 0L, null);
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(String ownerEmail, Integer roomId, RoomUpdateRequest request) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);

        if (request.getName() != null && !request.getName().isBlank()) {
            room.setName(request.getName().trim());
        }
        if (request.getLevelId() != null) {
            room.setLevel(levelRepository.findById(request.getLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy trình độ id=" + request.getLevelId())));
        }
        if (request.getCapacity() != null) {
            // Hạ sức chứa xuống dưới sĩ số hiện tại thì phải mời người ra —
            // việc đó cần chủ đích, không được xảy ra như tác dụng phụ của
            // một lần sửa số.
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
        if (request.getStatus() != null) {
            requireHasExamBeforeOpening(room, request.getStatus());
            room.setStatus(request.getStatus());
        }
        if (request.getStartTime() != null) {
            room.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            room.setEndTime(request.getEndTime());
        }

        roomRepository.save(room);
        return describe(room, owner, true);
    }

    @Override
    @Transactional
    public void deleteRoom(String ownerEmail, Integer roomId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);

        // Đếm cả người đã rời đi: xoá phòng là xoá luôn dấu vết ai từng ở đó.
        if (memberRepository.countSeatsIssued(roomId) > 0) {
            throw new BusinessException(
                    "Phòng đã có người tham gia. Hãy đóng phòng thay vì xoá, "
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
    @Transactional
    public void kickMember(String ownerEmail, Integer roomId, String userId) {
        User owner = requireUser(ownerEmail);
        requireOwnedRoom(roomId, owner);

        RoomMember member = memberRepository.findById_RoomIdAndId_UserId(roomId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Người này không có trong phòng"));
        if (!member.isActive()) {
            throw new BusinessException("Người này đã không còn trong phòng");
        }
        member.deactivate(MemberStatus.KICKED, DbTime.now());
        memberRepository.save(member);
        log.info("Mời ra khỏi phòng roomId={} userId={}", roomId, userId);
    }

    @Override
    @Transactional
    public RoomResponse attachExam(String ownerEmail, Integer roomId, Integer examId) {
        User owner = requireUser(ownerEmail);
        Room room = requireOwnedRoom(roomId, owner);

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đề thi id=" + examId));
        // Chỉ gắn được đề của chính mình. Thiếu bước này thì ai cũng gắn được
        // đề của người khác vào phòng mình và phát tán nội dung của họ.
        if (exam.getCreatedBy() == null
                || !exam.getCreatedBy().getUserId().equals(owner.getUserId())) {
            throw new UnauthorizedException("Chỉ gắn được đề thi do chính bạn tạo");
        }
        if (roomExamRepository.existsById_RoomIdAndId_ExamId(roomId, examId)) {
            throw new BusinessException("Đề thi này đã có trong phòng");
        }

        long current = roomExamRepository.countById_RoomId(roomId);
        roomExamRepository.save(RoomExam.builder()
                .id(new RoomExamKey(roomId, examId))
                .room(room)
                .exam(exam)
                .orderNo((int) current + 1)
                .build());

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
        // Chỉ gỡ liên kết. Đề vẫn còn nguyên trong ngân hàng, và bài đã làm
        // vẫn còn nguyên vì chúng trỏ tới đề chứ không trỏ tới phòng.
        roomExamRepository.deleteById(key);
        return describe(room, owner, true);
    }

    // ── Phía thí sinh ──────────────────────────────────────────────────────

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
        List<Room> rooms = roomRepository.findOpenRooms(RoomStatus.OPEN);
        // Người ngoài không được thấy mã phòng — thấy mã thì cơ chế vào bằng
        // mã của những phòng CODE cũng mất ý nghĩa nếu chúng lọt vào danh sách này.
        return toResponses(rooms, user, false);
    }

    /**
     * Vào phòng bằng mã — nơi "ai nhanh thì vào" thật sự diễn ra.
     *
     * Cách hiển nhiên là {@code SELECT COUNT(*)}, so với sức chứa, rồi
     * {@code INSERT}. Cách đó SAI: hai request gần như cùng lúc đều đọc được
     * cùng một con số cũ, cùng kết luận "còn chỗ", và phòng 50 chỗ nhận 52 người.
     *
     * Ở đây khoá dòng PHÒNG trước khi đếm ({@code findByIdForUpdate}), nên hai
     * người tranh cùng một phòng buộc phải xếp hàng — và chỉ họ mới phải xếp
     * hàng, hai phòng khác nhau vẫn nhận người song song. Ràng buộc
     * UNIQUE(RoomID, SeatNo) là lưới an toàn cuối cùng, thứ vẫn đúng kể cả khi
     * mai kia có ai viết một đường vào phòng khác mà quên khoá.
     *
     * Idempotent: người đã ở trong phòng gọi lại nhận về đúng ghế cũ. Người đã
     * rời đi thì được nhận lại ghế CŨ chứ không phải ghế mới — ghế đã cấp thì
     * không bao giờ cấp lại cho ai khác, nên nó vẫn còn đó chờ họ.
     */
    @Override
    @Transactional
    public RoomResponse join(String userEmail, RoomJoinRequest request) {
        User user = requireUser(userEmail);
        String code = request.getCode() == null ? "" : request.getCode().trim().toUpperCase();

        Room found = roomRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không có phòng nào mang mã này"));

        // Khoá dòng phòng. Từ đây tới hết transaction, không ai khác cấp được
        // ghế trong cùng phòng.
        Room room = roomRepository.findByIdForUpdate(found.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng"));

        Optional<RoomMember> existing =
                memberRepository.findById_RoomIdAndId_UserId(room.getRoomId(), user.getUserId());

        if (existing.isPresent()) {
            RoomMember member = existing.get();
            if (member.getStatus() == MemberStatus.KICKED) {
                throw new UnauthorizedException("Bạn đã bị mời ra khỏi phòng này");
            }
            if (!member.isActive()) {
                // Quay lại phòng đã rời: nhận lại đúng ghế cũ.
                requireAcceptingMembers(room);
                member.setStatus(MemberStatus.ACTIVE);
                member.setLeftAt(null);
                memberRepository.save(member);
            }
            return describe(room, user, true);
        }

        requireAcceptingMembers(room);

        // Đọc CÓ KHOÁ, không phải đếm thường. Khoá dòng phòng ở trên xếp đúng
        // thứ tự hai request, nhưng nó KHÔNG làm mới ảnh chụp dữ liệu của
        // transaction — một câu COUNT thường ở đây vẫn đọc ra con số cũ và cấp
        // trùng ghế. Xem RoomMemberRepository.findMaxSeatForUpdate.
        //
        // Ghế của người đã rời đi vẫn tính là đã cấp: MAX chứ không phải sĩ số.
        int lastSeat = memberRepository.findMaxSeatForUpdate(room.getRoomId());
        if (!room.hasRoomFor(lastSeat)) {
            throw new BusinessException("Phòng đã đủ " + room.getCapacity() + " người");
        }

        RoomMember member = RoomMember.builder()
                .id(new RoomMemberKey(room.getRoomId(), user.getUserId()))
                .room(room)
                .user(user)
                .seatNo(lastSeat + 1)
                .status(MemberStatus.ACTIVE)
                .build();

        try {
            // flush ngay để va chạm UNIQUE nổ ra ở ĐÂY, nơi còn dịch được thành
            // một câu tiếng Việt tử tế, thay vì lúc commit — chỗ đó đã ra khỏi
            // service và người dùng chỉ nhận về một trang lỗi 500 trần trụi.
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // Tới được đây nghĩa là hai người vẫn giành được cùng một ghế dù đã
            // khoá. Ràng buộc UNIQUE là lưới an toàn cuối và nó vừa làm đúng
            // việc của mình; phần còn lại chỉ là nói cho người dùng biết sự thật.
            log.warn("Tranh ghế ở phòng roomId={}: {}", room.getRoomId(), e.getMessage());
            throw new BusinessException("Phòng vừa hết chỗ, có người vào trước bạn");
        }

        log.info("Vào phòng roomId={} userId={} ghế={}",
                room.getRoomId(), user.getUserId(), member.getSeatNo());
        return describe(room, user, true);
    }

    @Override
    @Transactional
    public void leave(String userEmail, Integer roomId) {
        User user = requireUser(userEmail);
        RoomMember member = memberRepository
                .findById_RoomIdAndId_UserId(roomId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn không ở trong phòng này"));
        if (!member.isActive()) {
            return; // đã rời rồi, không có gì để làm
        }
        member.deactivate(MemberStatus.LEFT, DbTime.now());
        memberRepository.save(member);
    }

    // ── Hỗ trợ ──────────────────────────────────────────────────────────────

    /** Phòng có nhận người mới không, và có đang trong khung giờ không. */
    private void requireAcceptingMembers(Room room) {
        if (!room.getStatus().acceptsNewMembers()) {
            throw new BusinessException(switch (room.getStatus()) {
                case DRAFT -> "Phòng chưa mở";
                case RUNNING -> "Phòng đã bắt đầu thi, không nhận thêm người";
                case CLOSED -> "Phòng đã đóng";
                default -> "Phòng không nhận thêm người";
            });
        }
    }

    /** Không cho mở một phòng rỗng: thí sinh vào rồi chẳng có gì để làm. */
    private void requireHasExamBeforeOpening(Room room, RoomStatus target) {
        if (target == RoomStatus.OPEN
                && roomExamRepository.countById_RoomId(room.getRoomId()) == 0) {
            throw new BusinessException("Phòng chưa có đề thi nào, chưa mở được");
        }
    }

    private Room requireOwnedRoom(Integer roomId, User owner) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng thi id=" + roomId));
        if (!room.isOwnedBy(owner.getUserId())) {
            // 404 chứ không 403: người ngoài không cần biết phòng đó có tồn tại.
            throw new ResourceNotFoundException("Không tìm thấy phòng thi id=" + roomId);
        }
        return room;
    }

    /** Dựng response cho một phòng đơn lẻ, tự đếm các con số cần thiết. */
    private RoomResponse describe(Room room, User viewer, boolean revealCode) {
        long members = memberRepository.countActive(room.getRoomId());
        long exams = roomExamRepository.countById_RoomId(room.getRoomId());
        RoomMember mine = memberRepository
                .findById_RoomIdAndId_UserId(room.getRoomId(), viewer.getUserId())
                .orElse(null);
        return toResponse(room, viewer, revealCode, members, exams, mine);
    }

    /**
     * Dựng response cho cả một danh sách phòng.
     *
     * Gộp phần đếm thành hai truy vấn cho toàn bộ danh sách thay vì hai truy vấn
     * cho mỗi phòng — màn hình này hiện 5–20 phòng cùng lúc.
     */
    private List<RoomResponse> toResponses(List<Room> rooms, User viewer, boolean revealCode) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Integer> roomIds = rooms.stream().map(Room::getRoomId).toList();

        Map<Integer, Long> memberCounts = new HashMap<>();
        for (RoomMemberRepository.RoomHeadcount row
                : memberRepository.countActiveByRoomIdIn(roomIds)) {
            memberCounts.put(row.getRoomId(), row.getTotal());
        }
        Map<Integer, Long> examCounts = new HashMap<>();
        for (RoomExamRepository.RoomExamCount row
                : roomExamRepository.countByRoomIdIn(roomIds)) {
            examCounts.put(row.getRoomId(), row.getTotal());
        }

        return rooms.stream().map(room -> {
            RoomMember mine = memberRepository
                    .findById_RoomIdAndId_UserId(room.getRoomId(), viewer.getUserId())
                    .orElse(null);
            return toResponse(room, viewer, revealCode,
                    memberCounts.getOrDefault(room.getRoomId(), 0L),
                    examCounts.getOrDefault(room.getRoomId(), 0L),
                    mine);
        }).toList();
    }

    private RoomResponse toResponse(Room room, User viewer, boolean revealCode,
                                    long memberCount, long examCount, RoomMember mine) {
        SubjectLevel level = room.getLevel();
        boolean isOwner = room.isOwnedBy(viewer.getUserId());
        // Mã phòng chỉ dành cho chủ phòng và thành viên. Lộ mã cho người ngoài
        // là vô hiệu hoá chính cơ chế vào phòng bằng mã.
        boolean showCode = revealCode && (isOwner || (mine != null && mine.isActive()));

        return RoomResponse.builder()
                .roomId(room.getRoomId())
                .name(room.getName())
                .code(showCode ? room.getCode() : null)
                .ownerName(room.getOwner() == null ? null : room.getOwner().getFullName())
                .levelId(level == null ? null : level.getLevelId())
                .levelName(level == null ? null : level.getLevelName())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .capacity(room.getCapacity())
                .memberCount(memberCount)
                .seatsLeft(room.seatsLeft(memberCount))
                .joinPolicy(room.getJoinPolicy())
                .status(room.getStatus())
                .startTime(room.getStartTime())
                .endTime(room.getEndTime())
                .examCount(examCount)
                .owner(isOwner)
                .myStatus(mine == null ? null : mine.getStatus())
                .mySeatNo(mine == null ? null : mine.getSeatNo())
                .build();
    }

    /** Mã ngẫu nhiên, thử lại khi trùng. */
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
        // Đụng đây nghĩa là không gian mã đã gần đầy — lúc đó phải nới CODE_LENGTH
        // chứ không phải thử thêm vài lần nữa.
        throw new BusinessException("Không sinh được mã phòng, vui lòng thử lại");
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
}
