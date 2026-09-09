package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kết quả một lần làm lại câu sai.
 *
 * Đây là chỗ duy nhất lộ đáp án đúng và lời giải — sau khi người học đã trả
 * lời xong. Trước đó thì không endpoint nào trả hai thứ này.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MistakeAttemptResponse {

    private Integer questionId;

    private boolean correct;

    /** Null với câu tự luận: dạng đó không có một đáp án đúng duy nhất. */
    private Integer correctAnswerId;

    private String explanation;

    private Integer correctStreak;

    /** true nếu lần đúng này khiến câu rời khỏi sổ tay. */
    private boolean mastered;

    /** Mốc ôn kế tiếp. Null khi câu đã được đánh dấu là đã sửa xong. */
    private LocalDateTime nextReviewAt;

    /** Số câu còn lại chưa sửa được, để client cập nhật huy hiệu ngay. */
    private long remainingOpen;
}
