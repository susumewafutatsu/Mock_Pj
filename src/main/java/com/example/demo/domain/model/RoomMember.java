package com.example.demo.domain.model;

import com.example.demo.domain.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Một người trong một phòng thi, kèm số ghế.
 *
 * Vì sao có {@link #seatNo} chứ không chỉ đếm số dòng: "ai nhanh thì vào" là
 * một cuộc tranh chấp thật. Hai trăm người bấm Tham gia cùng lúc vào phòng năm
 * mươi chỗ, mà cách kiểm kiểu {@code SELECT COUNT(*)} rồi {@code INSERT} thì
 * cả hai request đều đọc được con số cũ và cùng chen vào.
 *
 * Ghế là chốt chặn cuối cùng: {@code UNIQUE (RoomID, SeatNo)} khiến hai người
 * không thể nhận cùng một số ghế, dù tầng trên có sai thế nào. Tầng service
 * khoá dòng phòng khi cấp ghế để chuyện đó không xảy ra ngay từ đầu, còn ràng
 * buộc này là lưới an toàn — thứ vẫn đúng kể cả khi mai kia có ai viết một
 * đường vào phòng khác mà quên khoá.
 *
 * Rời phòng hay bị mời ra chỉ đổi {@link #status}, không xoá dòng và không trả
 * lại ghế: cấp lại ghế cũ cho người mới sẽ làm số ghế không còn khớp với lịch
 * sử làm bài.
 */
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

    /** Số ghế, đếm từ 1 theo thứ tự vào phòng. Không bao giờ được cấp lại. */
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

    /** Còn là thành viên đang hoạt động của phòng. */
    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /** Rời phòng hoặc bị mời ra — giữ nguyên ghế, chỉ đổi trạng thái. */
    public void deactivate(MemberStatus reason, LocalDateTime now) {
        this.status = reason;
        this.leftAt = now;
    }
}
