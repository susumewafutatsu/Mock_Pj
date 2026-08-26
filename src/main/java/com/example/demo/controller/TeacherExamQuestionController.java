package com.example.demo.controller;

import com.example.demo.dto.request.ExamQuestionSelection;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.service.ExamSnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Gắn / bỏ câu hỏi trong một đề thi, kèm việc chụp snapshot. */
@RestController
@RequestMapping("/api/teacher/exams/{examId}/questions")
@RequiredArgsConstructor
public class TeacherExamQuestionController {

    private final ExamSnapshotService snapshotService;

    /** Thêm câu hỏi vào đề. Snapshot nội dung + đáp án được chụp ngay tại đây. */
    @PostMapping
    public ApiResponse<Map<String, Object>> attach(
            @PathVariable Integer examId,
            @Valid @RequestBody List<ExamQuestionSelection> selections,
            @AuthenticationPrincipal UserDetails me) {
        int added = snapshotService.attachQuestions(examId, selections, me.getUsername());
        return ApiResponse.success("Đã thêm " + added + " câu hỏi vào đề",
                Map.of("added", added));
    }

    /**
     * Cập nhật snapshot theo bản mới nhất trong ngân hàng câu hỏi.
     * Trả 409 nếu đề đã có học sinh làm bài.
     */
    @PostMapping("/{questionId}/refresh-snapshot")
    public ApiResponse<Void> refresh(@PathVariable Integer examId,
                                     @PathVariable Integer questionId,
                                     @AuthenticationPrincipal UserDetails me) {
        snapshotService.refreshSnapshot(examId, questionId, me.getUsername());
        return ApiResponse.success("Đã cập nhật lại nội dung câu hỏi trong đề", null);
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> detach(@PathVariable Integer examId,
                                    @PathVariable Integer questionId,
                                    @AuthenticationPrincipal UserDetails me) {
        snapshotService.detachQuestion(examId, questionId, me.getUsername());
        return ApiResponse.success("Đã bỏ câu hỏi khỏi đề", null);
    }
}
