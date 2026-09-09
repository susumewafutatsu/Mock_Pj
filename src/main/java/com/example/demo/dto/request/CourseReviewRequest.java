package com.example.demo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin duyệt hoặc từ chối một khoá học.
 *
 * {@link #note} bắt buộc khi TỪ CHỐI — từ chối mà không nói lý do thì tác giả
 * chỉ biết là bị trả về, không biết phải sửa gì, và sẽ gửi lại đúng bản cũ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseReviewRequest {

    private String note;
}
