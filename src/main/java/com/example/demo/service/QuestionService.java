package com.example.demo.service;

import com.example.demo.dto.request.QuestionSearchRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.QuestionSummaryResponse;
import org.springframework.data.domain.Pageable;

// TODO: Implement question management
// - createQuestion(QuestionCreateRequest) → save to QuestionBank
// - getQuestionsByBank(Long bankId)       → list questions
// - updateQuestion(Long id, ...)
// - deleteQuestion(Long id)
// - filterByDifficulty(difficulty, count) → used by adaptive engine
public interface QuestionService {

    /**
     * Lọc / tìm kiếm câu hỏi theo tag và các tiêu chí khác.
     *
     * @param request  bộ tiêu chí lọc, field nào null/rỗng thì bỏ qua
     * @param pageable phân trang + sắp xếp (đã được whitelist field sort ở controller)
     */
    PageResponse<QuestionSummaryResponse> searchQuestions(QuestionSearchRequest request, Pageable pageable);
}
