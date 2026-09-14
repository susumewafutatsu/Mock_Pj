package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Soạn / sửa một đoạn văn đọc hiểu. */
@Data
public class ReadingPassageRequest {

    /** Tiêu đề ngắn để người ra đề nhận ra đoạn nào, thí sinh cũng thấy. */
    private String title;

    @NotBlank(message = "Nội dung bài đọc không được để trống")
    private String content;
}
