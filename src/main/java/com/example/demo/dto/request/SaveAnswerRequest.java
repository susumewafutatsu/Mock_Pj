package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Một lần học sinh chọn / sửa đáp án của một câu hỏi.
 *
 * Được gửi ngay khi học sinh bấm chọn, không đợi tới lúc nộp bài. Server lưu
 * theo kiểu upsert trên (SubmissionID, QuestionID) nên gửi lại nhiều lần cho
 * cùng một câu là an toàn — dòng cũ bị ghi đè, không sinh thêm dòng.
 */
@Data
public class SaveAnswerRequest {

    @NotNull(message = "Thiếu questionId")
    private Integer questionId;

    /**
     * Đáp án được chọn, lấy từ snapshot của đề thi (không phải AnswerID trong
     * ngân hàng câu hỏi). Dùng cho câu trắc nghiệm.
     * Null = bỏ chọn.
     */
    private Integer snapshotAnswerId;

    /** Nội dung câu tự luận. Null hoặc rỗng = xoá phần đã viết. */
    private String essayResponse;
}
