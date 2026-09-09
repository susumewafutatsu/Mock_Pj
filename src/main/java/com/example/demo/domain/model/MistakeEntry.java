package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.example.demo.util.DbTime;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một câu hỏi mà thí sinh đã từng làm sai, kèm lịch ôn lại.
 *
 * Không gắn với bài làm nào cả: sai cùng một câu ở ba đề khác nhau vẫn chỉ là
 * MỘT dòng, với {@code wrongCount = 3}. Đó là điểm khác biệt so với
 * {@link SubmissionDetail} — bảng kia ghi "trong bài làm đó bạn trả lời gì",
 * bảng này trả lời "câu này bạn còn yếu tới mức nào".
 *
 * Lịch ôn ở đây cố ý đơn giản hơn SM-2 dùng cho thẻ ghi nhớ: câu hỏi thi có
 * đề bài dài, ôn lại tốn thời gian hơn nhiều so với lật một thẻ từ vựng, nên
 * chỉ cần giãn theo cấp số nhân thô là đủ. Xem {@link #scheduleAfter}.
 */
@Entity
@Table(
        name = "MistakeEntries",
        uniqueConstraints = @UniqueConstraint(
                name = "UC_Mistake_User_Question",
                columnNames = {"UserID", "QuestionID"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MistakeEntry {

    /** Đúng liên tiếp bao nhiêu lần thì coi như đã sửa được. */
    public static final int STREAK_TO_MASTER = 2;

    /** Số ngày giãn cách theo số lần đúng liên tiếp: lần 1 sau 1 ngày, lần 2 sau 3 ngày. */
    private static final int[] REVIEW_GAP_DAYS = {1, 3};

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MistakeID")
    private Integer mistakeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QuestionID", nullable = false)
    private Question question;

    @Column(name = "WrongCount", nullable = false)
    @Builder.Default
    private Integer wrongCount = 1;

    @Column(name = "CorrectStreak", nullable = false)
    @Builder.Default
    private Integer correctStreak = 0;

    @Column(name = "LastWrongAt")
    private LocalDateTime lastWrongAt;

    @Column(name = "NextReviewAt")
    private LocalDateTime nextReviewAt;

    /** Khác null = đã sửa được, không còn nằm trong hàng đợi ôn tập. */
    @Column(name = "MasteredAt")
    private LocalDateTime masteredAt;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    // ── Hành vi ────────────────────────────────────────────────────────────

    /** Đã sửa được câu này chưa. */
    public boolean isMastered() {
        return masteredAt != null;
    }

    /** Tới hạn ôn lại chưa. Chưa có lịch thì coi như tới hạn ngay. */
    public boolean isDueAt(LocalDateTime now) {
        return !isMastered() && (nextReviewAt == null || !now.isBefore(nextReviewAt));
    }

    /**
     * Ghi nhận một lần làm sai nữa.
     *
     * Chuỗi đúng bị reset về 0 và câu được đưa trở lại hàng đợi ngay hôm nay:
     * vừa sai xong mà hẹn ba ngày nữa mới ôn thì đúng lúc ôn đã quên sạch bối
     * cảnh. Câu đã từng "thuộc" mà sai lại cũng bị mở khoá trở lại — nhớ được
     * một lần không có nghĩa là nhớ mãi.
     */
    public void recordWrong(LocalDateTime now) {
        wrongCount = (wrongCount == null ? 0 : wrongCount) + 1;
        correctStreak = 0;
        masteredAt = null;
        lastWrongAt = now;
        // Cắt về giây: cột DATETIME làm tròn lên phần giây lẻ, đủ để một câu
        // vừa làm sai bị coi là "chưa tới hạn" ngay sau khi ghi. Xem DbTime.
        nextReviewAt = DbTime.atSecond(now);
    }

    /**
     * Ghi nhận một lần trả lời đúng khi ôn lại.
     *
     * @return true nếu lần đúng này khiến câu được đánh dấu đã sửa xong
     */
    public boolean recordCorrect(LocalDateTime now) {
        correctStreak = (correctStreak == null ? 0 : correctStreak) + 1;
        if (correctStreak >= STREAK_TO_MASTER) {
            masteredAt = now;
            nextReviewAt = null;
            return true;
        }
        nextReviewAt = scheduleAfter(now, correctStreak);
        return false;
    }

    /** Mốc ôn kế tiếp sau lần đúng thứ {@code streak}. */
    private static LocalDateTime scheduleAfter(LocalDateTime now, int streak) {
        int index = Math.min(streak, REVIEW_GAP_DAYS.length) - 1;
        return DbTime.atSecond(now.plusDays(REVIEW_GAP_DAYS[Math.max(index, 0)]));
    }
}
