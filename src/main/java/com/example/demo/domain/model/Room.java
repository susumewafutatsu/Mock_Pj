package com.example.demo.domain.model;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Phòng thi — thứ thay thế cho Lớp học.
 *
 * Khác biệt cốt lõi so với mô hình lớp cũ, và cũng là lý do đổi:
 *
 *   - Lớp là một danh sách CỐ ĐỊNH do người ra đề thêm từng người. Phòng thi là
 *     một chỗ RỖNG có sức chứa, ai tới trước ngồi trước.
 *   - Đề thi trước đây thuộc về đúng một lớp ({@code Exams.ClassID}). Giờ quan
 *     hệ đi qua {@link RoomExam}, nên một đề dùng lại được ở nhiều phòng — thứ
 *     mô hình lớp không làm được.
 *   - Thí sinh không cần "được thêm vào" nữa: họ tự vào bằng mã phòng.
 *
 * Phòng không có người ra đề/thí sinh, chỉ có chủ phòng ({@link #owner}) và
 * thành viên ({@link RoomMember}).
 */
@Entity
@Table(name = "Rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RoomID")
    private Integer roomId;

    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    /**
     * Mã tham gia — thứ người ra đề đọc cho cả phòng.
     *
     * Sinh ngẫu nhiên, không đoán được từ RoomID: mã chạy tuần tự thì ai cũng
     * mò được vào phòng của người khác chỉ bằng cách đếm lên.
     */
    @Column(name = "Code", nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OwnerID", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelID")
    private SubjectLevel level;

    /**
     * Sức chứa. {@code null} = không giới hạn.
     *
     * Việc chặn KHÔNG nằm ở cột này: nó chỉ là con số để so. Chốt chặn thật là
     * khoá dòng phòng lúc cấp ghế cộng với UNIQUE(RoomID, SeatNo) — xem
     * {@link RoomMember#getSeatNo()}.
     */
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

    @Column(name = "StartTime")
    private LocalDateTime startTime;

    @Column(name = "EndTime")
    private LocalDateTime endTime;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    // ── Hành vi ────────────────────────────────────────────────────────────

    /** Phòng không giới hạn số người. */
    public boolean isUnlimited() {
        return capacity == null || capacity <= 0;
    }

    /** Đã ngồi {@code taken} ghế thì còn chỗ không. */
    public boolean hasRoomFor(long taken) {
        return isUnlimited() || taken < capacity;
    }

    /** Số ghế trống, hoặc {@code null} khi phòng không giới hạn. */
    public Integer seatsLeft(long taken) {
        return isUnlimited() ? null : (int) Math.max(capacity - taken, 0);
    }

    /** Người đó có phải chủ phòng không. */
    public boolean isOwnedBy(String userId) {
        return owner != null && owner.getUserId().equals(userId);
    }
}
