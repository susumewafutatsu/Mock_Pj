package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Tạo mới một ngân hàng câu hỏi. Chủ sở hữu lấy từ token, không nhận từ client. */
@Data
public class QuestionBankCreateRequest {

    @NotBlank(message = "Tên ngân hàng câu hỏi không được để trống")
    @Size(max = 200, message = "Tên ngân hàng câu hỏi tối đa 200 ký tự")
    private String title;

    /** Cấp độ/môn học của ngân hàng. Có thể null nếu chưa phân loại. */
    private Integer levelId;

    @Size(max = 255)
    private String sourceDocumentUrl;
}
