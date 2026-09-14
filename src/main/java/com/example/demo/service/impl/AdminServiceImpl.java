package com.example.demo.service.impl;

import com.example.demo.domain.enums.CourseStatus;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.AdminStatsResponse;
import com.example.demo.dto.response.AdminUserPageResponse;
import com.example.demo.dto.response.AdminUserResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.QuestionBankRepository;
import com.example.demo.repository.RoomRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Quản lý tài khoản: khoá, mở khoá, đổi vai trò. */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int LOCK_REASON_MAX = 255;

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final QuestionBankRepository questionBankRepository;
    private final RoomRepository roomRepository;
    private final CourseRepository courseRepository;

    // ── Tổng quan ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        LocalDateTime now = LocalDateTime.now();
        return AdminStatsResponse.builder()
                .totalUsers(userRepository.count())
                .students(userRepository.countByRole(Role.STUDENT))
                .teachers(userRepository.countByRole(Role.TEACHER))
                .admins(userRepository.countByRole(Role.ADMIN))
                .lockedUsers(userRepository.countByLockedTrue())
                .newUsersLast7Days(userRepository.countByCreatedAtGreaterThanEqual(now.minusDays(7)))
                .exams(examRepository.count())
                .publicExams(examRepository.countByIsPublicTrue())
                .questionBanks(questionBankRepository.count())
                .rooms(roomRepository.count())
                .pendingCourses(courseRepository.countByStatus(CourseStatus.PENDING))
                .sessionsInProgress(submissionRepository.countByStatus(SubmissionStatus.IN_PROGRESS))
                .sessionsAtRisk(submissionRepository.countByStatusAndAtRiskStatusTrue(
                        SubmissionStatus.IN_PROGRESS))
                .submittedLast24h(submissionRepository.countBySubmittedAtGreaterThanEqual(now.minusHours(24)))
                .serverTime(now)
                .build();
    }

    // ── Danh sách người dùng ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(String adminEmail, String role, Boolean locked,
                                           String q, int page, int size) {
        Role roleFilter = role == null || role.isBlank() ? null : parseRole(role);
        String pattern = q == null || q.isBlank()
                ? null
                : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // Người mới nhất lên đầu: admin mở trang này thường là để xem ai vừa đăng ký.
        PageRequest pageable = PageRequest.of(Math.max(0, page), safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("email")));

        Page<User> result = userRepository.searchForAdmin(roleFilter, locked, pattern, pageable);

        Map<String, Long> roleCounts = new LinkedHashMap<>();
        for (Role r : Role.values()) {
            roleCounts.put(r.name(), userRepository.countByRole(r));
        }

        List<AdminUserResponse> users = new ArrayList<>(result.getNumberOfElements());
        for (User user : result.getContent()) {
            users.add(toResponse(user, adminEmail));
        }

        return AdminUserPageResponse.builder()
                .users(users)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .roleCounts(roleCounts)
                .lockedCount(userRepository.countByLockedTrue())
                .build();
    }

    // ── Đổi vai trò ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AdminUserResponse changeRole(String adminEmail, String userId, String role) {
        User target = requireEditable(adminEmail, userId);
        if (role == null || role.isBlank()) {
            throw new BusinessException("Chưa chọn vai trò mới");
        }
        Role next = parseRole(role);
        if (next == Role.ADMIN) {
            throw new BusinessException("Không cấp quyền quản trị qua giao diện. "
                    + "Việc này phải làm trực tiếp trong cơ sở dữ liệu.");
        }
        if (next == target.getRole()) {
            return toResponse(target, adminEmail);
        }

        if (target.getRole() == Role.STUDENT
                && submissionRepository.existsByStudent_UserIdAndStatus(
                        target.getUserId(), SubmissionStatus.IN_PROGRESS)) {
            throw new BusinessException(target.getFullName() + " đang làm dở một bài thi. "
                    + "Đổi vai trò lúc này sẽ đá thí sinh ra khỏi phòng thi giữa chừng — "
                    + "đợi bài được nộp rồi đổi.");
        }
        if (target.getRole() == Role.TEACHER) {
            requireOwnsNothing(target);
        }

        Role previous = target.getRole();
        target.setRole(next);
        userRepository.save(target);
        log.info("Admin {} đổi vai trò {} ({}): {} -> {}",
                adminEmail, target.getEmail(), target.getUserId(), previous, next);
        return toResponse(target, adminEmail);
    }

    /** Người ra đề chuyển thành thí sinh thì mọi thứ họ sở hữu thành mồ côi. */
    private void requireOwnsNothing(User teacher) {
        String id = teacher.getUserId();
        List<String> owned = new ArrayList<>();
        long exams = examRepository.countByCreatedBy_UserId(id);
        long banks = questionBankRepository.countByTeacher_UserId(id);
        long rooms = roomRepository.countByOwner_UserId(id);
        long courses = courseRepository.countByAuthor_UserId(id);
        if (exams > 0) owned.add(exams + " đề thi");
        if (banks > 0) owned.add(banks + " ngân hàng câu hỏi");
        if (rooms > 0) owned.add(rooms + " phòng thi");
        if (courses > 0) owned.add(courses + " khoá học");
        if (!owned.isEmpty()) {
            throw new BusinessException(teacher.getFullName() + " đang sở hữu "
                    + String.join(", ", owned) + ". Chuyển thành thí sinh thì sẽ không còn ai "
                    + "quản lý được chúng. Nếu muốn chặn người này, hãy khoá tài khoản.");
        }
    }

    // ── Khoá / mở khoá ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public AdminUserResponse lock(String adminEmail, String userId, String reason) {
        User target = requireEditable(adminEmail, userId);
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("Phải ghi lý do khoá — người mở khoá sau này cần biết vì sao.");
        }
        if (trimmed.length() > LOCK_REASON_MAX) {
            throw new BusinessException("Lý do tối đa " + LOCK_REASON_MAX + " ký tự.");
        }
        if (target.isLocked()) {
            throw new BusinessException("Tài khoản này đã bị khoá từ " + target.getLockedAt() + ".");
        }

        target.setLocked(true);
        target.setLockedAt(LocalDateTime.now());
        target.setLockReason(trimmed);
        userRepository.save(target);
        // Thí sinh đang dở bài thi: phiên KHÔNG bị huỷ.
        log.info("Admin {} khoá tài khoản {} ({}): {}",
                adminEmail, target.getEmail(), target.getUserId(), trimmed);
        return toResponse(target, adminEmail);
    }

    @Override
    @Transactional
    public AdminUserResponse unlock(String adminEmail, String userId) {
        User target = requireEditable(adminEmail, userId);
        if (!target.isLocked()) {
            return toResponse(target, adminEmail);
        }
        target.setLocked(false);
        target.setLockedAt(null);
        target.setLockReason(null);
        userRepository.save(target);
        log.info("Admin {} mở khoá tài khoản {} ({})",
                adminEmail, target.getEmail(), target.getUserId());
        return toResponse(target, adminEmail);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private User requireEditable(String adminEmail, String userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng id=" + userId));
        if (target.getEmail().equalsIgnoreCase(adminEmail)) {
            throw new BusinessException("Không thể tự khoá hoặc đổi vai trò của chính mình.");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new BusinessException("Không sửa tài khoản quản trị qua giao diện. "
                    + "Việc này phải làm trực tiếp trong cơ sở dữ liệu.");
        }
        return target;
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Vai trò không hợp lệ: " + role);
        }
    }

    private AdminUserResponse toResponse(User user, String adminEmail) {
        boolean editable = user.getRole() != Role.ADMIN
                && !user.getEmail().equalsIgnoreCase(adminEmail);
        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .authProvider(user.getAuthProvider() == null ? null : user.getAuthProvider().name())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .locked(user.isLocked())
                .lockedAt(user.getLockedAt())
                .lockReason(user.getLockReason())
                .editable(editable)
                .build();
    }
}
