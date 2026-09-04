package com.example.demo.controller;

import com.example.demo.dto.request.QuestionSearchRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.QuestionSummaryResponse;
import com.example.demo.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock
    private QuestionService questionService;

    @InjectMocks
    private QuestionController questionController;

    @Captor
    private ArgumentCaptor<QuestionSearchRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(questionController).build();
        when(questionService.searchQuestions(any(), any()))
                .thenReturn(PageResponse.<QuestionSummaryResponse>builder()
                        .items(List.of())
                        .page(0)
                        .size(20)
                        .totalItems(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .build());
    }

    @Test
    void searchQuestions_bindsRepeatedTagParamsAndTagMode() throws Exception {
        mockMvc.perform(get("/api/v1/questions/search")
                        .param("tag", "dai-so")
                        .param("tag", "lop-10")
                        .param("tagMode", "ALL")
                        .param("difficulty", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verifyCaptured();
        QuestionSearchRequest request = requestCaptor.getValue();
        assertEquals(List.of("dai-so", "lop-10"), request.getTag());
        assertEquals(QuestionSearchRequest.TagMode.ALL, request.getTagMode());
        assertEquals(3, request.getDifficulty());
    }

    @Test
    void searchQuestions_usesDefaultPagingAndSortWhenNotProvided() throws Exception {
        mockMvc.perform(get("/api/v1/questions/search")).andExpect(status().isOk());

        verifyCaptured();
        assertEquals(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                pageableCaptor.getValue());
    }

    @Test
    void searchQuestions_capsPageSizeAndRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/questions/search")
                        .param("page", "-5")
                        .param("size", "5000"))
                .andExpect(status().isOk());

        verifyCaptured();
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void searchQuestions_fallsBackToDefaultSortWhenFieldIsNotWhitelisted() throws Exception {
        mockMvc.perform(get("/api/v1/questions/search")
                        .param("sort", "content,asc"))
                .andExpect(status().isOk());

        verifyCaptured();
        assertEquals(Sort.by(Sort.Direction.ASC, "createdAt"), pageableCaptor.getValue().getSort());
    }

    @Test
    void searchQuestions_acceptsWhitelistedSortField() throws Exception {
        mockMvc.perform(get("/api/v1/questions/search")
                        .param("sort", "difficultyLevel,asc"))
                .andExpect(status().isOk());

        verifyCaptured();
        assertEquals(Sort.by(Sort.Direction.ASC, "difficultyLevel"), pageableCaptor.getValue().getSort());
    }

    private void verifyCaptured() {
        org.mockito.Mockito.verify(questionService)
                .searchQuestions(requestCaptor.capture(), pageableCaptor.capture());
    }
}
