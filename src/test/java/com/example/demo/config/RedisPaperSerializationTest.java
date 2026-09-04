package com.example.demo.config;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.dto.response.ExamOptionView;
import com.example.demo.dto.response.ExamQuestionView;
import com.example.demo.service.cache.CachedExamPaper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Bản đề thi có đi vào Redis rồi quay ra nguyên vẹn không.
 *
 * Test này tồn tại vì đây là chỗ hỏng âm thầm: cache ghi được nhưng đọc ra sai
 * kiểu thì lỗi chỉ nổ ở request thứ hai của học sinh, khi cache đã ấm — tức là
 * đúng lúc đang thi thật chứ không phải lúc chạy thử.
 *
 * Ba thứ dễ vỡ được kiểm ở đây: LocalDateTime (cần JavaTimeModule),
 * BigDecimal (điểm số, sai kiểu là sai điểm) và kiểu phần tử của List (thiếu
 * default typing thì Jackson trả về LinkedHashMap).
 *
 * Không cần Redis chạy: chỉ kiểm tra đúng lớp serializer mà RedisConfig lắp vào.
 */
class RedisPaperSerializationTest {

    private final GenericJackson2JsonRedisSerializer serializer =
            new RedisConfig().redisJsonSerializer();

    @Test
    void bandDeThiQuaRedisVanGiuNguyenKieuDuLieu() {
        List<ExamQuestionView> paper = List.of(
                ExamQuestionView.builder()
                        .questionId(7)
                        .questionOrder(1)
                        .points(new BigDecimal("2.50"))
                        .content("Chọn cách đọc đúng của 水")
                        .questionType(QuestionType.MULTIPLE_CHOICE)
                        .options(List.of(
                                ExamOptionView.builder()
                                        .snapshotAnswerId(11).answerContent("みず").answerOrder(1).build(),
                                ExamOptionView.builder()
                                        .snapshotAnswerId(12).answerContent("ひ").answerOrder(2).build()))
                        .answeredAt(LocalDateTime.of(2026, 9, 1, 8, 30, 15))
                        .build());

        Object restored = serializer.deserialize(serializer.serialize(new CachedExamPaper(paper)));

        assertInstanceOf(CachedExamPaper.class, restored);
        List<?> list = ((CachedExamPaper) restored).getQuestions();
        assertEquals(1, list.size());

        // Nếu default typing bị tắt, dòng này nổ ClassCastException — đúng cái
        // lỗi mà test này canh.
        assertInstanceOf(ExamQuestionView.class, list.get(0));
        ExamQuestionView question = (ExamQuestionView) list.get(0);

        assertEquals(7, question.getQuestionId());
        assertEquals("Chọn cách đọc đúng của 水", question.getContent());
        assertEquals(QuestionType.MULTIPLE_CHOICE, question.getQuestionType());
        // So bằng compareTo: 2.50 và 2.5 bằng nhau về giá trị nhưng equals() thì không.
        assertEquals(0, new BigDecimal("2.50").compareTo(question.getPoints()));
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 30, 15), question.getAnsweredAt());

        assertNotNull(question.getOptions());
        assertEquals(2, question.getOptions().size());
        assertInstanceOf(ExamOptionView.class, question.getOptions().get(0));
        assertEquals("みず", question.getOptions().get(0).getAnswerContent());
    }
}
