package com.example.demo.service;

import com.example.demo.dto.response.ExamOptionView;
import com.example.demo.dto.response.ExamQuestionView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Xáo đề theo lượt làm. */
class PaperShufflerTest {

    /** 12 câu: phần 1 có câu 1–6 (câu 3–5 cùng một bài đọc), phần 2 có câu 7–12. */
    private static List<ExamQuestionView> paper() {
        List<ExamQuestionView> list = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            List<ExamOptionView> options = new ArrayList<>();
            for (int k = 1; k <= 4; k++) {
                options.add(ExamOptionView.builder().snapshotAnswerId(i * 10 + k).answerContent("opt" + k)
                        .answerOrder(k).build());
            }
            list.add(ExamQuestionView.builder()
                    .questionId(i)
                    .sectionId(i <= 6 ? 1 : 2)
                    .passageId(i >= 3 && i <= 5 ? 77 : null)
                    .options(options)
                    .build());
        }
        return list;
    }

    private static List<Integer> ids(List<ExamQuestionView> list) {
        return list.stream().map(ExamQuestionView::getQuestionId).toList();
    }

    @Test
    @DisplayName("Cùng một lượt (cùng hạt giống) luôn ra cùng thứ tự — F5 không làm đổi đề")
    void onDinhTheoLuot() {
        assertEquals(ids(PaperShuffler.shuffle(paper(), true, false, 42)),
                ids(PaperShuffler.shuffle(paper(), true, false, 42)));
    }

    @Test
    @DisplayName("Lượt khác nhau thì thứ tự khác nhau")
    void khacLuotKhacThuTu() {
        boolean anyDifferent = IntStream.range(1, 20).anyMatch(seed ->
                !ids(PaperShuffler.shuffle(paper(), true, false, seed))
                        .equals(ids(PaperShuffler.shuffle(paper(), true, false, seed + 1000))));
        assertTrue(anyDifferent);
    }

    @Test
    @DisplayName("Câu không nhảy phần, câu cùng bài đọc đứng liền nhau và giữ thứ tự")
    void giuRangBuoc() {
        for (long seed = 1; seed <= 50; seed++) {
            List<Integer> order = ids(PaperShuffler.shuffle(paper(), true, false, seed));
            // 6 câu đầu vẫn là câu của phần 1
            assertTrue(order.subList(0, 6).stream().allMatch(id -> id <= 6), "seed " + seed);
            int at = order.indexOf(3);
            assertEquals(List.of(3, 4, 5), order.subList(at, at + 3), "seed " + seed);
            assertEquals(12, order.stream().distinct().count());
        }
    }

    @Test
    @DisplayName("Xáo đáp án không làm mất đáp án nào; tắt cả hai cờ thì giữ nguyên")
    void xaoDapAn() {
        List<ExamQuestionView> shuffled = PaperShuffler.shuffle(paper(), false, true, 7);
        for (ExamQuestionView q : shuffled) {
            assertEquals(4, q.getOptions().stream().map(ExamOptionView::getSnapshotAnswerId).distinct().count());
        }
        boolean anyMoved = shuffled.stream().anyMatch(q -> q.getOptions().get(0).getAnswerOrder() != 1);
        assertTrue(anyMoved);

        List<Integer> original = ids(paper());
        assertEquals(original, ids(PaperShuffler.shuffle(paper(), false, false, 7)));
        assertNotEquals(null, PaperShuffler.shuffle(List.of(), true, true, 1));
    }
}
