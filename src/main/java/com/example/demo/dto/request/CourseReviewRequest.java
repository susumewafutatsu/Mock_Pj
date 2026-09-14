package com.example.demo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin duyệt hoặc từ chối một khoá học. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseReviewRequest {

    private String note;
}
