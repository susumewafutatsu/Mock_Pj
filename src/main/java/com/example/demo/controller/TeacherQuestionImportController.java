package com.example.demo.controller;

import com.example.demo.dto.request.AiGenerateRequest;
import com.example.demo.dto.request.QuestionCreateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.service.AiQuestionService;
import com.example.demo.service.DocumentTextExtractorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Import câu hỏi từ file PDF/Word: trích văn bản, rồi nhờ Gemini sinh câu hỏi
 * nháp. Câu hỏi chỉ được lưu thật vào ngân hàng khi giáo viên xác nhận qua
 * {@code POST /api/teacher/question-banks/{bankId}/questions/bulk}.
 */
@RestController
@RequestMapping("/api/teacher/question-banks/{bankId}/imports")
@RequiredArgsConstructor
public class TeacherQuestionImportController {

    private final DocumentTextExtractorService documentTextExtractorService;
    private final AiQuestionService aiQuestionService;

    /** Bước 1: đọc file, trả về văn bản thô để giáo viên xem trước khi sinh câu hỏi. */
    @PostMapping("/extract-text")
    public ApiResponse<Map<String, String>> extractText(
            @PathVariable Integer bankId,
            @RequestParam("file") MultipartFile file) {
        String text = documentTextExtractorService.extractText(file);
        return ApiResponse.success("Đã trích xuất nội dung file", Map.of("text", text));
    }

    /** Bước 2: từ văn bản đã trích, nhờ Gemini sinh câu hỏi nháp (chưa lưu). */
    @PostMapping("/generate")
    public ApiResponse<List<QuestionCreateRequest>> generate(
            @PathVariable Integer bankId,
            @Valid @RequestBody AiGenerateRequest request) {
        List<QuestionCreateRequest> drafts = aiQuestionService.generateFromText(request);
        return ApiResponse.success("Đã sinh " + drafts.size() + " câu hỏi, hãy kiểm tra lại trước khi lưu",
                drafts);
    }
}
