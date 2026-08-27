package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Ngân hàng câu hỏi của một giáo viên, dùng cho danh sách chọn ngân hàng
 * ở trang quản lý câu hỏi.
 */
@Data
@Builder
public class QuestionBankResponse {
    private Integer bankId;
    private String title;
    private Integer levelId;
    /** Tên cấp độ/môn để hiển thị, có thể null nếu ngân hàng chưa gán cấp độ. */
    private String levelName;
    private String sourceDocumentUrl;
    private LocalDateTime createdAt;
    /** Số câu hỏi còn hiệu lực (không tính câu đã xoá mềm). */
    private long totalQuestions;
}
