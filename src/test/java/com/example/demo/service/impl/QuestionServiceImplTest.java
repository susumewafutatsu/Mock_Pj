package com.example.demo.service.impl;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.model.Question;
import com.example.demo.domain.model.QuestionBank;
import com.example.demo.domain.model.Tag;
import com.example.demo.dto.request.QuestionSearchRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.QuestionSummaryResponse;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private QuestionServiceImpl questionService;

    @Test
    void searchQuestions_mapsResultAndIncludesTagsSortedByName() {
        when(tagRepository.findExistingTagNames(any())).thenReturn(List.of("dai-so"));
        when(questionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(question()), PAGEABLE, 1));

        QuestionSearchRequest request = QuestionSearchRequest.builder()
                .tag(List.of("dai-so"))
                .build();

        PageResponse<QuestionSummaryResponse> result = questionService.searchQuestions(request, PAGEABLE);

        assertEquals(1, result.getTotalItems());
        QuestionSummaryResponse item = result.getItems().get(0);
        assertEquals(7, item.getQuestionId());
        assertEquals(3, item.getBankId());
        assertEquals(List.of("dai-so", "lop-10"),
                item.getTags().stream().map(tag -> tag.getTagName()).toList());
    }

    @Test
    void searchQuestions_skipsDatabaseQueryWhenNoRequestedTagExists() {
        when(tagRepository.findExistingTagNames(any())).thenReturn(List.of());

        QuestionSearchRequest request = QuestionSearchRequest.builder()
                .tag(List.of("khong-ton-tai"))
                .build();

        PageResponse<QuestionSummaryResponse> result = questionService.searchQuestions(request, PAGEABLE);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getTotalPages());
        verify(questionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchQuestions_withTagModeAll_skipsQueryWhenOneTagIsMissing() {
        when(tagRepository.findExistingTagNames(any())).thenReturn(List.of("dai-so"));

        QuestionSearchRequest request = QuestionSearchRequest.builder()
                .tag(List.of("dai-so", "khong-ton-tai"))
                .tagMode(QuestionSearchRequest.TagMode.ALL)
                .build();

        PageResponse<QuestionSummaryResponse> result = questionService.searchQuestions(request, PAGEABLE);

        assertTrue(result.getItems().isEmpty());
        verify(questionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchQuestions_withoutTagFilter_doesNotTouchTagRepository() {
        when(questionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(question()), PAGEABLE, 1));

        QuestionSearchRequest request = QuestionSearchRequest.builder()
                .difficulty(3)
                .build();

        PageResponse<QuestionSummaryResponse> result = questionService.searchQuestions(request, PAGEABLE);

        assertEquals(1, result.getItems().size());
        verify(tagRepository, never()).findExistingTagNames(any());
    }

    @Test
    void searchQuestions_doesNotExposeAnswersOrExplanation() {
        when(questionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(question()), PAGEABLE, 1));

        PageResponse<QuestionSummaryResponse> result =
                questionService.searchQuestions(new QuestionSearchRequest(), PAGEABLE);

        // QuestionSummaryResponse cố tình không có field answers/explanation
        List<String> fieldNames = java.util.Arrays.stream(QuestionSummaryResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertTrue(fieldNames.stream().noneMatch(name -> name.contains("answer")));
        assertTrue(fieldNames.stream().noneMatch(name -> name.contains("explanation")));
        assertEquals(1, result.getItems().size());
    }

    private Question question() {
        // Thứ tự tag cố tình đảo để kiểm tra việc sắp xếp theo tên khi map DTO
        LinkedHashSet<Tag> tags = new LinkedHashSet<>(List.of(
                Tag.builder().tagId(2).tagName("lop-10").build(),
                Tag.builder().tagId(1).tagName("dai-so").build()
        ));

        return Question.builder()
                .questionId(7)
                .bank(QuestionBank.builder().bankId(3).build())
                .content("1 + 1 = ?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .difficultyLevel(1)
                .isAiGenerated(false)
                .explanation("Cộng hai số")
                .tags(tags)
                .build();
    }
}
