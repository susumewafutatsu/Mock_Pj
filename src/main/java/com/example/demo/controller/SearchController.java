package com.example.demo.controller;

import com.example.demo.domain.model.Course;
import com.example.demo.domain.model.Exam;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ô "Tìm…" trên thanh trên. */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    /** Mỗi nhóm tối đa chừng này kết quả: đây là ô gợi ý, không phải trang kết quả. */
    private static final int LIMIT = 8;

    private final ExamRepository examRepository;
    private final CourseRepository courseRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> search(@RequestParam("q") String q) {
        String keyword = q == null ? "" : q.trim();
        Map<String, Object> result = new LinkedHashMap<>();
        // Dưới hai ký tự thì gần như khớp mọi thứ — vừa vô ích vừa nặng.
        if (keyword.length() < 2) {
            result.put("exams", List.of());
            result.put("courses", List.of());
            return ApiResponse.success(result);
        }

        List<Map<String, Object>> exams = examRepository.searchPublic(keyword, PageRequest.of(0, LIMIT))
                .stream().map(this::examItem).toList();
        List<Map<String, Object>> courses = courseRepository.searchPublished(keyword, PageRequest.of(0, LIMIT))
                .stream().map(this::courseItem).toList();

        result.put("exams", exams);
        result.put("courses", courses);
        return ApiResponse.success(result);
    }

    private Map<String, Object> examItem(Exam exam) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("examId", exam.getExamId());
        item.put("title", exam.getTitle());
        item.put("levelName", exam.getLevel() == null ? null : exam.getLevel().getLevelName());
        item.put("durationMinutes", exam.getDurationMinutes());
        return item;
    }

    private Map<String, Object> courseItem(Course course) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("courseId", course.getCourseId());
        item.put("title", course.getTitle());
        item.put("levelName", course.getLevel() == null ? null : course.getLevel().getLevelName());
        return item;
    }
}
