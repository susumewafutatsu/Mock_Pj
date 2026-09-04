package com.example.demo.controller;

import com.example.demo.dto.request.QuestionSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.QuestionSummaryResponse;
import com.example.demo.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    /** Chỉ cho phép sort theo các field này — chặn việc truyền property lạ gây lỗi 500. */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("questionId", "difficultyLevel", "createdAt", "questionType");

    private static final String DEFAULT_SORT_FIELD = "createdAt";
    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionService questionService;

    @Operation(
            summary = "Lọc / tìm kiếm câu hỏi theo tag",
            description = """
                    Tag có thể truyền nhiều lần: ?tag=dai-so&tag=lop-10.
                    tagMode=ANY (mặc định) trả câu hỏi có ít nhất 1 tag; tagMode=ALL yêu cầu có đủ tất cả tag.
                    Kết quả không kèm đáp án — lấy chi tiết qua GET /api/v1/questions/{questionId}."""
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<QuestionSummaryResponse>>> searchQuestions(
            @Valid @ModelAttribute QuestionSearchRequest request,
            @Parameter(description = "Số trang, bắt đầu từ 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sắp xếp dạng field,asc|desc — vd createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        Pageable pageable = buildPageable(page, size, sort);
        PageResponse<QuestionSummaryResponse> result = questionService.searchQuestions(request, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Tìm kiếm câu hỏi thành công", result));
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, parseSort(sort));
    }

    /** Parse "field,direction". Field không nằm trong whitelist thì rơi về mặc định createdAt,desc. */
    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD);
        }

        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            field = DEFAULT_SORT_FIELD;
        }

        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, field);
    }
}
