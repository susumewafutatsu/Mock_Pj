package com.example.demo.domain.model;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.RoomPhase;
import com.example.demo.domain.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Phòng thi: một buổi thi cho một đề. */
@Entity
@Table(name = "Rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    public static final int DEFAULT_LATE_JOIN_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoomID")
    private Integer roomId;

    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    /** Mã tham gia. */
    @Column(name = "Code", nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OwnerID", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelID")
    private SubjectLevel level;

    /** Sức chứa. {@code null} = không giới hạn. */
    @Column(name = "Capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "JoinPolicy", nullable = false, length = 20)
    @Builder.Default
    private JoinPolicy joinPolicy = JoinPolicy.CODE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private RoomStatus status = RoomStatus.DRAFT;

    /** Giờ bắt đầu làm bài: hẹn trước hoặc lúc bấm bắt đầu. */
    @Column(name = "StartTime")
    private LocalDateTime startTime;

    /** Giờ kết thúc. {@code null} = tính theo thời lượng đề. */
    @Column(name = "EndTime")
    private LocalDateTime endTime;

    /** Lời dặn hiện ở sảnh chờ. */
    @Column(name = "Instructions", columnDefinition = "TEXT")
    private String instructions;

    /** Số phút đầu buổi thi vẫn cho vào phòng. 0 = không cho vào muộn. */
    @Column(name = "LateJoinMinutes", nullable = false)
    @Builder.Default
    private Integer lateJoinMinutes = DEFAULT_LATE_JOIN_MINUTES;

    @Column(name = "ReminderSentAt")
    private LocalDateTime reminderSentAt;

    @Column(name = "StartNotifiedAt")
    private LocalDateTime startNotifiedAt;

    @Column(name = "EndNotifiedAt")
    private LocalDateTime endNotifiedAt;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    // ── Hành vi ────────────────────────────────────────────────────────────

    public boolean isUnlimited() {
        return capacity == null || capacity <= 0;
    }

    /** Đã ngồi {@code taken} ghế thì còn chỗ không. */
    public boolean hasRoomFor(long taken) {
        return isUnlimited() || taken < capacity;
    }

    /** Số ghế trống, hoặc {@code null} khi không giới hạn. */
    public Integer seatsLeft(long taken) {
        return isUnlimited() ? null : (int) Math.max(capacity - taken, 0);
    }

    public boolean isOwnedBy(String userId) {
        return owner != null && owner.getUserId().equals(userId);
    }

    /** Mốc hết giờ của buổi thi. */
    public LocalDateTime endAt(Integer examMinutes) {
        if (endTime != null) {
            return endTime;
        }
        if (startTime == null || examMinutes == null) {
            return null;
        }
        return startTime.plusMinutes(examMinutes);
    }

    /** Mốc cuối cùng còn cho vào phòng khi đang thi; null nếu chưa bắt đầu. */
    public LocalDateTime lateJoinUntil(Integer examMinutes) {
        if (startTime == null) {
            return null;
        }
        int minutes = lateJoinMinutes == null ? 0 : Math.max(0, lateJoinMinutes);
        LocalDateTime until = startTime.plusMinutes(minutes);
        LocalDateTime end = endAt(examMinutes);
        return end != null && end.isBefore(until) ? end : until;
    }

    /** Buổi thi đã từng bắt đầu (kể cả đã kết thúc). */
    public boolean hasStarted(LocalDateTime now) {
        return status == RoomStatus.RUNNING
                || (startTime != null && !now.isBefore(startTime) && status != RoomStatus.DRAFT);
    }

    /** Phòng có nhận người vào tại thời điểm {@code now} không. */
    public boolean acceptsMembersAt(LocalDateTime now, Integer examMinutes) {
        RoomPhase phase = phaseAt(now, examMinutes);
        if (phase == RoomPhase.WAITING) {
            return true;
        }
        if (phase == RoomPhase.IN_PROGRESS) {
            LocalDateTime until = lateJoinUntil(examMinutes);
            return until != null && now.isBefore(until);
        }
        return false;
    }

    /** Pha của phòng tại thời điểm {@code now}. */
    public RoomPhase phaseAt(LocalDateTime now, Integer examMinutes) {
        if (status == RoomStatus.DRAFT) {
            return RoomPhase.DRAFT;
        }
        if (status == RoomStatus.CLOSED) {
            return RoomPhase.ENDED;
        }
        LocalDateTime end = endAt(examMinutes);
        if (end != null && !now.isBefore(end)) {
            return RoomPhase.ENDED;
        }
        boolean started = status == RoomStatus.RUNNING
                || (startTime != null && !now.isBefore(startTime));
        return started ? RoomPhase.IN_PROGRESS : RoomPhase.WAITING;
    }
}
