package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Xác nhận đã lưu một câu trả lời. Nhẹ nhất có thể vì được gọi rất nhiều lần.
 *
 * Có kèm thời gian còn lại để mỗi lần autosave cũng là một lần đồng bộ lại
 * đồng hồ đếm ngược với server.
 */
@Data
@Builder
public class AnswerSavedResponse {

    private Integer submissionId;
    private Integer questionId;
    private LocalDateTime answeredAt;
    private LocalDateTime serverTime;
    private LocalDateTime expiresAt;
    private long remainingSeconds;

    /** Số câu đã có đáp án lưu trên server, để client hiện "đã làm x/y". */
    private int answeredQuestions;
}
