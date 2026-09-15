package com.example.demo.service;

import com.example.demo.dto.request.AiGenerateRequest;
import com.example.demo.dto.request.QuestionCreateRequest;

import java.util.List;

/** Sinh câu hỏi nháp từ văn bản bằng Gemini — chưa lưu vào ngân hàng, chờ giáo viên duyệt. */
public interface AiQuestionService {

    List<QuestionCreateRequest> generateFromText(AiGenerateRequest request);
}
