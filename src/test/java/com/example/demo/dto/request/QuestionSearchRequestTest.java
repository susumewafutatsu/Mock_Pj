package com.example.demo.dto.request;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSearchRequestTest {

    @Test
    void normalizedTags_trimsLowercasesAndDeduplicates() {
        QuestionSearchRequest request = QuestionSearchRequest.builder()
                .tag(List.of("  Dai-So ", "DAI-SO", "lop-10", "   "))
                .build();

        assertEquals(List.of("dai-so", "lop-10"), List.copyOf(request.normalizedTags()));
    }

    @Test
    void normalizedTags_returnsEmptyWhenNoTagProvided() {
        assertTrue(QuestionSearchRequest.builder().build().normalizedTags().isEmpty());
    }

    @Test
    void normalizedTags_ignoresNullEntries() {
        QuestionSearchRequest request = QuestionSearchRequest.builder()
                .tag(Arrays.asList("hinh-hoc", null))
                .build();

        assertEquals(List.of("hinh-hoc"), List.copyOf(request.normalizedTags()));
    }

    @Test
    void likeSafeKeyword_escapesWildcardsSoUserInputIsTreatedAsLiteral() {
        QuestionSearchRequest request = QuestionSearchRequest.builder()
                .keyword("100% _ đúng")
                .build();

        assertEquals("100\\% \\_ đúng", request.likeSafeKeyword());
    }

    @Test
    void likeSafeKeyword_returnsNullForBlankKeyword() {
        assertNull(QuestionSearchRequest.builder().keyword("   ").build().likeSafeKeyword());
        assertNull(QuestionSearchRequest.builder().build().likeSafeKeyword());
    }

    @Test
    void resolvedTagMode_defaultsToAnyWhenNull() {
        QuestionSearchRequest request = new QuestionSearchRequest();
        request.setTagMode(null);

        assertEquals(QuestionSearchRequest.TagMode.ANY, request.resolvedTagMode());
    }
}
