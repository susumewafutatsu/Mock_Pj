package com.example.demo.controller;

import com.example.demo.dto.request.ExamCreateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.TeacherExamResponse;
import com.example.demo.service.TeacherExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Quản lý đề thi của giáo viên.
 *
 * Câu hỏi của đề nằm ở {@link TeacherExamQuestionController}
 * ({@code /api/teacher/exams/{examId}/questions}) — tạo đề xong phải gắn câu
 * hỏi, trước đó đề ở trạng thái NO_QUESTIONS và học sinh chưa vào thi được.
 *
 * Base path: /api/teacher/exams
 */
@RestController
@RequestMapping("/api/teacher/exams")
@RequiredArgsConstructor
public class TeacherExamController {

    private final TeacherExamService teacherExamService;

    @GetMapping
    public ApiResponse<List<TeacherExamResponse>> listMine(
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(teacherExamService.listMine(me.getUsername()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeacherExamResponse>> create(
            @Valid @RequestBody ExamCreateRequest request,
            @AuthenticationPrincipal UserDetails me) {
        TeacherExamResponse created = teacherExamService.create(me.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo đề thi", created));
    }

    @PutMapping("/{examId}")
    public ApiResponse<TeacherExamResponse> update(
            @PathVariable Integer examId,
            @Valid @RequestBody ExamCreateRequest request,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã cập nhật đề thi",
                teacherExamService.update(me.getUsername(), examId, request));
    }

    @DeleteMapping("/{examId}")
    public ApiResponse<Void> delete(
            @PathVariable Integer examId,
            @AuthenticationPrincipal UserDetails me) {
        teacherExamService.delete(me.getUsername(), examId);
        return ApiResponse.success("Đã xóa đề thi", null);
    }
}
