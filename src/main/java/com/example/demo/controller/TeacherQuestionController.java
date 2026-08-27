package com.example.demo.controller;

import com.example.demo.dto.request.QuestionCreateRequest;
import com.example.demo.dto.request.QuestionUpdateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.QuestionResponse;
import com.example.demo.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Quản lý câu hỏi — chỉ giáo viên. Role đã được chặn ở SecurityConfig
 * ({@code /api/teacher/** -> hasRole("TEACHER")}); quyền sở hữu ngân hàng câu
 * hỏi được kiểm tra thêm một lần nữa trong service.
 *
 * Mọi phản hồi bọc trong {@link ApiResponse} theo đúng quy ước của
 * {@link AuthController} — frontend đọc {@code response.data.data}.
 */
@RestController
@RequestMapping("/api/teacher/question-banks/{bankId}/questions")
@RequiredArgsConstructor
public class TeacherQuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionResponse>> create(
            @PathVariable Integer bankId,
            @Valid @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal UserDetails me) {
        request.setBankId(bankId);
        QuestionResponse created = questionService.create(request, me.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã thêm câu hỏi", created));
    }

    @GetMapping
    public ApiResponse<Page<QuestionResponse>> list(
            @PathVariable Integer bankId,
            @AuthenticationPrincipal UserDetails me,
            Pageable pageable) {
        return ApiResponse.success(questionService.listByBank(bankId, me.getUsername(), pageable));
    }

    @GetMapping("/{questionId}")
    public ApiResponse<QuestionResponse> getOne(
            @PathVariable Integer bankId,
            @PathVariable Integer questionId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(questionService.getOne(bankId, questionId, me.getUsername()));
    }

    @PutMapping("/{questionId}")
    public ApiResponse<QuestionResponse> update(
            @PathVariable Integer bankId,
            @PathVariable Integer questionId,
            @Valid @RequestBody QuestionUpdateRequest request,
            @AuthenticationPrincipal UserDetails me) {
        QuestionResponse updated =
                questionService.update(bankId, questionId, request, me.getUsername());
        return ApiResponse.success("Đã cập nhật câu hỏi", updated);
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> delete(
            @PathVariable Integer bankId,
            @PathVariable Integer questionId,
            @AuthenticationPrincipal UserDetails me) {
        questionService.delete(bankId, questionId, me.getUsername());
        return ApiResponse.success("Đã xoá câu hỏi", null);
    }
}
