package com.example.demo.domain.model;

import com.example.demo.domain.enums.ReviewGrade;
import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.service.srs.Sm2Scheduler;
import com.example.demo.util.DbTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trạng thái ôn tập của MỘT người trên MỘT thẻ — nơi giữ toàn bộ tiến độ học.
 *
 * Cùng một thẻ với hai người là hai dòng khác nhau: lịch ôn phụ thuộc vào việc
 * từng người nhớ tới đâu, không phụ thuộc vào bản thân thẻ.
 *
 * Ghi danh một bộ thẻ = tạo các dòng ở đây với {@code dueAt} bằng thời điểm ghi
 * danh, nên thẻ mới xuất hiện ngay trong hàng đợi hôm nay. Không có bảng ghi
 * danh riêng: sự tồn tại của dòng này chính là bằng chứng người đó đang học thẻ.
 */
@Entity
@Table(
        name = "UserCardStates",
        uniqueConstraints = @UniqueConstraint(
                name = "UC_CardState_User_Item",
                columnNames = {"UserID", "ItemType", "ItemID"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCardState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CardStateID")
    private Integer cardStateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ItemType", nullable = false, length = 10)
    private StudyItemType itemType;

    /** Tham chiếu đa hình tới VocabItems hoặc KanjiItems, tuỳ {@link #itemType}. */
    @Column(name = "ItemID", nullable = false)
    private Integer itemId;

    @Column(name = "EaseFactor", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal easeFactor = Sm2Scheduler.DEFAULT_EASE;

    @Column(name = "IntervalDays", nullable = false)
    @Builder.Default
    private Integer intervalDays = 0;

    @Column(name = "Repetitions", nullable = false)
    @Builder.Default
    private Integer repetitions = 0;

    @Column(name = "Lapses", nullable = false)
    @Builder.Default
    private Integer lapses = 0;

    @Column(name = "DueAt", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "LastReviewedAt")
    private LocalDateTime lastReviewedAt;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    // ── Hành vi ────────────────────────────────────────────────────────────

    /** Thẻ chưa từng được ôn lần nào. */
    public boolean isNew() {
        return repetitions == null || repetitions == 0;
    }

    /** Đã vào trí nhớ dài hạn (khoảng cách ôn từ 21 ngày trở lên). */
    public boolean isMature() {
        return intervalDays != null && intervalDays >= Sm2Scheduler.MATURE_INTERVAL_DAYS;
    }

    /** Đến hạn ôn chưa, tính theo giờ server. */
    public boolean isDueAt(LocalDateTime now) {
        return dueAt != null && !now.isBefore(dueAt);
    }

    /**
     * Áp một lần ôn vào thẻ: tính lại hệ số dễ, khoảng cách và mốc đến hạn.
     *
     * Thẻ bị quên có {@code intervalDays = 0} nên {@code dueAt} rơi đúng vào
     * thời điểm hiện tại — nó quay lại ngay trong phiên học hôm nay chứ không
     * đợi sang mai.
     */
    public void applyReview(ReviewGrade grade, LocalDateTime now) {
        Sm2Scheduler.Outcome outcome = Sm2Scheduler.next(
                easeFactor,
                intervalDays == null ? 0 : intervalDays,
                repetitions == null ? 0 : repetitions,
                lapses == null ? 0 : lapses,
                grade);

        easeFactor = outcome.easeFactor();
        intervalDays = outcome.intervalDays();
        repetitions = outcome.repetitions();
        lapses = outcome.lapses();
        lastReviewedAt = now;
        // Cắt về giây trước khi ghi: xem DbTime để biết vì sao nửa giây làm
        // tròn lên đủ để một thẻ vừa trả lời "Quên" không quay lại được.
        dueAt = DbTime.atSecond(now.plusDays(outcome.intervalDays()));
    }
}
