package com.example.demo.domain.model;

import com.example.demo.domain.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Một người trong một phòng thi, kèm số ghế. */
@Entity
@Table(
        name = "RoomMembers",
        uniqueConstraints = @UniqueConstraint(
                name = "UC_RoomMember_Room_Seat",
                columnNames = {"RoomID", "SeatNo"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {

    /** Không báo có mặt quá chừng này giây thì coi như đã rời trang phòng. */
    public static final int ONLINE_WINDOW_SECONDS = 60;

    @EmbeddedId
    private RoomMemberKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roomId")
    @JoinColumn(name = "RoomID")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "UserID")
    private User user;

    /** Số ghế theo thứ tự vào phòng. Không cấp lại. */
    @Column(name = "SeatNo", nullable = false)
    private Integer seatNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "JoinedAt", updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "LeftAt")
    private LocalDateTime leftAt;

    /** Lần cuối trang phòng của thí sinh báo có mặt. */
    @Column(name = "LastSeenAt")
    private LocalDateTime lastSeenAt;

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /** Đang mở trang phòng. */
    public boolean isOnlineAt(LocalDateTime now) {
        return lastSeenAt != null && lastSeenAt.isAfter(now.minusSeconds(ONLINE_WINDOW_SECONDS));
    }

    /** Rời phòng hoặc bị mời ra — giữ ghế, chỉ đổi trạng thái. */
    public void deactivate(MemberStatus reason, LocalDateTime now) {
        this.status = reason;
        this.leftAt = now;
    }
}
