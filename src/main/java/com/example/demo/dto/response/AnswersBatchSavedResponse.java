package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Xác nhận đã lưu một lô đáp án. */
@Data
@Builder
public class AnswersBatchSavedResponse {

    private Integer submissionId;
    private List<Integer> savedQuestionIds;
    private LocalDateTime answeredAt;
    private LocalDateTime serverTime;
    private LocalDateTime expiresAt;
    private long remainingSeconds;

    /** Số câu đã có đáp án lưu trên server, để client hiện "đã làm x/y". */
    private int answeredQuestions;
}
