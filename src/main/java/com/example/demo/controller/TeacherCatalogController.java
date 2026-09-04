package com.example.demo.controller;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.SubjectLevelResponse;
import com.example.demo.service.SubjectLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Danh mục dùng chung cho các form của giáo viên (tạo lớp, tạo ngân hàng câu
 * hỏi, tạo đề thi)
 * Base path: /api/teacher
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherCatalogController {

    private final SubjectLevelService subjectLevelService;

    /**
     * GET /api/teacher/levels
     * Toàn bộ trình độ kèm tên môn học.
     */
    @GetMapping("/levels")
    public ApiResponse<List<SubjectLevelResponse>> listLevels() {
        return ApiResponse.success(subjectLevelService.listAll());
    }
}
