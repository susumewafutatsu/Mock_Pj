package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Một lựa chọn của câu hỏi, đọc từ snapshot của đề thi.
 *
 * KHÔNG có cờ đáp án đúng: DTO này đi ra tới trình duyệt của học sinh đang
 * làm bài.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamOptionView {

    /** ID trong bảng snapshot của đề — chính là giá trị client gửi lại khi chọn. */
    private Integer snapshotAnswerId;

    private String answerContent;

    private Integer answerOrder;
}
