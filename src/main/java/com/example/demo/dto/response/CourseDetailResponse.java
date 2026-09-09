package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Một khoá học kèm danh sách bài. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailResponse {

    private CourseResponse course;

    private List<LessonSummaryResponse> lessons;
}
