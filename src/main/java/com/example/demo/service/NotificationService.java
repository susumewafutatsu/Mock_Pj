package com.example.demo.service;

import com.example.demo.domain.enums.NotificationChannel;
import com.example.demo.domain.model.Notification;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Thông báo trong ứng dụng (ô chuông). */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Số thông báo trả về cho ô chuông. Cũ hơn thì không ai kéo xuống đọc. */
    private static final int LIST_LIMIT = 30;

    /** Các loại thông báo. Là chuỗi trong CSDL để thêm loại mới không cần migration. */
    public static final class Kind {
        public static final String ROOM_STARTED = "ROOM_STARTED";
        public static final String ROOM_REMINDER = "ROOM_REMINDER";
        public static final String ROOM_ADDED = "ROOM_ADDED";
        public static final String ROOM_KICKED = "ROOM_KICKED";
        public static final String ROOM_ENDED = "ROOM_ENDED";
        public static final String COURSE_REJECTED = "COURSE_REJECTED";
        public static final String COURSE_APPROVED = "COURSE_APPROVED";
        public static final String CARDS_DUE = "CARDS_DUE";

        private Kind() {
        }
    }

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate independent;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Gửi một thông báo. Không bao giờ ném lỗi ra ngoài — xem chú thích lớp. */
    public void notify(User user, String kind, String subject, String message, String link) {
        if (user == null) {
            return;
        }
        try {
            independent.executeWithoutResult(status -> notificationRepository.save(Notification.builder()
                    .user(user)
                    .channel(NotificationChannel.IN_APP)
                    .kind(kind)
                    .subject(subject)
                    .message(message)
                    .link(link)
                    .status("SENT")
                    .sentAt(LocalDateTime.now())
                    .build()));
        } catch (Exception e) {
            log.error("Không ghi được thông báo kind={} cho userId={}", kind, user.getUserId(), e);
        }
    }

    public void notifyAll(Collection<User> users, String kind, String subject, String message, String link) {
        for (User user : users) {
            notify(user, kind, subject, message, link);
        }
    }

    /** Hôm nay đã gửi loại này cho người này chưa. */
    @Transactional(readOnly = true)
    public boolean sentToday(String userId, String kind) {
        return notificationRepository.existsByUser_UserIdAndKindAndCreatedAtGreaterThanEqual(
                userId, kind, LocalDate.now().atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(String email) {
        User user = requireUser(email);
        return notificationRepository
                .findByUser_UserIdOrderByNotificationIdDesc(user.getUserId(), PageRequest.of(0, LIST_LIMIT))
                .stream()
                .map(n -> NotificationResponse.builder()
                        .notificationId(n.getNotificationId())
                        .kind(n.getKind())
                        .subject(n.getSubject())
                        .message(n.getMessage())
                        .link(n.getLink())
                        .read(Boolean.TRUE.equals(n.getIsRead()))
                        .createdAt(n.getCreatedAt() != null ? n.getCreatedAt() : n.getSentAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        return notificationRepository.countByUser_UserIdAndIsReadFalse(requireUser(email).getUserId());
    }

    @Transactional
    public void markRead(String email, Integer notificationId) {
        User user = requireUser(email);
        Notification n = notificationRepository.findById(notificationId)
                .filter(x -> x.getUser().getUserId().equals(user.getUserId()))
                // Thông báo của người khác thì với người gọi, nó không tồn tại.
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông báo id=" + notificationId));
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public int markAllRead(String email) {
        return notificationRepository.markAllRead(requireUser(email).getUserId());
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
    }
}
