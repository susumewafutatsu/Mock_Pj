package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Trình độ của một môn học — dùng cho các dropdown "Trình độ" ở form tạo lớp,
 * tạo ngân hàng câu hỏi, tạo đề thi.
 */
@Data
@Builder
public class SubjectLevelResponse {

    private Integer levelId;
    private String levelName;
    private Integer subjectId;
    private String subjectName;
    private Integer displayOrder;
}
