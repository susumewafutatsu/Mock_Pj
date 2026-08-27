package com.example.demo.controller;

import com.example.demo.dto.request.QuestionBankCreateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.QuestionBankResponse;
import com.example.demo.service.QuestionBankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ngân hàng câu hỏi của giáo viên. Trang quản lý câu hỏi cần endpoint này để
 * chọn ngân hàng trước khi thao tác với câu hỏi bên trong.
 */
@RestController
@RequestMapping("/api/teacher/question-banks")
@RequiredArgsConstructor
public class TeacherQuestionBankController {

    private final QuestionBankService bankService;

    @GetMapping
    public ApiResponse<List<QuestionBankResponse>> listMine(
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(bankService.listMine(me.getUsername()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionBankResponse>> create(
            @Valid @RequestBody QuestionBankCreateRequest request,
            @AuthenticationPrincipal UserDetails me) {
        QuestionBankResponse created = bankService.create(request, me.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo ngân hàng câu hỏi", created));
    }
}
