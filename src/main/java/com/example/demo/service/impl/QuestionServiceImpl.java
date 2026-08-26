package com.example.demo.service.impl;

import com.example.demo.domain.model.Question;
import com.example.demo.dto.request.QuestionSearchRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.QuestionSummaryResponse;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.TagRepository;
import com.example.demo.repository.specification.QuestionSpecifications;
import com.example.demo.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

// TODO: Implement QuestionService interface methods
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestionSummaryResponse> searchQuestions(
            QuestionSearchRequest request, Pageable pageable) {

        Set<String> tags = request.normalizedTags();

        // Tag yêu cầu nhưng không tồn tại trong bảng Tags thì chắc chắn không có câu hỏi nào khớp.
        // Chặn sớm ở đây để khỏi chạy query nặng lên bảng Questions.
        if (!tags.isEmpty() && !hasMatchableTags(tags, request.resolvedTagMode())) {
            return PageResponse.empty(pageable);
        }

        Specification<Question> spec = buildSpecification(request, tags);
        Page<Question> page = questionRepository.findAll(spec, pageable);

        return PageResponse.from(page, QuestionSummaryResponse::from);
    }

    /**
     * ANY: cần ít nhất 1 tag tồn tại. ALL: cần tất cả tag đều tồn tại.
     */
    private boolean hasMatchableTags(Set<String> tags, QuestionSearchRequest.TagMode mode) {
        Set<String> existing = Set.copyOf(tagRepository.findExistingTagNames(tags));
        return mode == QuestionSearchRequest.TagMode.ALL
                ? existing.containsAll(tags)
                : !existing.isEmpty();
    }

    private Specification<Question> buildSpecification(QuestionSearchRequest request, Set<String> tags) {
        Specification<Question> tagSpec = request.resolvedTagMode() == QuestionSearchRequest.TagMode.ALL
                ? QuestionSpecifications.hasAllTags(tags)
                : QuestionSpecifications.hasAnyTag(tags);

        return Specification.where(tagSpec)
                .and(QuestionSpecifications.contentContains(request.likeSafeKeyword()))
                .and(QuestionSpecifications.hasSubject(request.getSubjectId()))
                .and(QuestionSpecifications.hasLevel(request.getLevelId()))
                .and(QuestionSpecifications.hasBank(request.getBankId()))
                .and(QuestionSpecifications.hasDifficulty(request.getDifficulty()))
                .and(QuestionSpecifications.hasQuestionType(request.getQuestionType()))
                .and(QuestionSpecifications.isAiGenerated(request.getIsAiGenerated()));
    }
}
