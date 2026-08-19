package com.example.demo.domain.model;
import com.example.demo.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "Channel", length = 20)
    private NotificationChannel channel;

    @Column(name = "Subject", length = 200)
    private String subject;

    @Lob
    @Column(name = "Message", nullable = false)
    private String message;

    @Column(name = "Status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "SentAt")
    private LocalDateTime sentAt;
}