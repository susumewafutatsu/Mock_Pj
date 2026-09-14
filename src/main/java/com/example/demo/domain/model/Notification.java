package com.example.demo.domain.model;
import com.example.demo.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "Notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NotificationID")
    private Integer notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    // VARCHAR thay vì ENUM riêng của MySQL (xem chú thích trong ExamQuestion)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "Channel", length = 20)
    private NotificationChannel channel;

    @Column(name = "Subject", length = 200)
    private String subject;

    // LONGVARCHAR khớp LONGTEXT của changelog (xem chú thích trong Answer)
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "Message", nullable = false)
    private String message;

    @Column(name = "Status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "SentAt")
    private LocalDateTime sentAt;

    @Column(name = "IsRead", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /** Đường dẫn trong app để bấm vào thông báo là tới đúng chỗ cần làm. */
    @Column(name = "Link", length = 255)
    private String link;

    /** Loại thông báo (xem NotificationService.Kind) — để không nhắc trùng trong ngày. */
    @Column(name = "Kind", length = 40)
    private String kind;

    @Column(name = "CreatedAt", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}