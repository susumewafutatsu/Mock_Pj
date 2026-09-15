package com.example.demo.service;

import com.example.demo.dto.request.QuestionCreateRequest;
import com.example.demo.dto.request.QuestionSearchRequest;
import com.example.demo.dto.request.QuestionUpdateRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.QuestionResponse;
import com.example.demo.dto.response.QuestionSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Quản lý câu hỏi trong ngân hàng — nghiệp vụ của người ra đề. */
public interface QuestionService {

    QuestionResponse create(Integer bankId, QuestionCreateRequest request, String teacherEmail);

    /** Lưu hàng loạt câu hỏi đã được giáo viên duyệt sau khi import từ file. */
    List<QuestionResponse> createBulk(Integer bankId, List<QuestionCreateRequest> requests, String teacherEmail);

    QuestionResponse update(Integer bankId, Integer questionId,
                            QuestionUpdateRequest request, String teacherEmail);

    void delete(Integer bankId, Integer questionId, String teacherEmail);

    Page<QuestionResponse> listByBank(Integer bankId, String teacherEmail, Pageable pageable);

    QuestionResponse getOne(Integer bankId, Integer questionId, String teacherEmail);

    /** Dùng cho adaptive engine: lọc câu hỏi theo khoảng độ khó. */
    List<QuestionResponse> filterByDifficulty(Integer bankId, int minDifficulty,
                                              int maxDifficulty, String teacherEmail);

    /** Lọc / tìm kiếm câu hỏi theo tag và các tiêu chí khác. */
    PageResponse<QuestionSummaryResponse> searchQuestions(QuestionSearchRequest request, Pageable pageable);
}
