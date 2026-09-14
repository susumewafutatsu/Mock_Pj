package com.example.demo.service;

import com.example.demo.domain.model.ExamSection;
import com.example.demo.dto.response.ExamSectionView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lịch chạy các phần thi. */
class ExamSectionTimingTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 13, 8, 0);

    private static ExamSection section(int id, int minutes, int order) {
        return ExamSection.builder().sectionId(id).name("Phần " + order)
                .durationMinutes(minutes).orderNo(order).build();
    }

    private final ExamSectionTiming timing = new ExamSectionTiming(START,
            List.of(section(1, 20, 1), section(2, 40, 2), section(3, 30, 3)));

    @Test
    @DisplayName("Các phần nối tiếp: phần 2 mở đúng lúc phần 1 khoá")
    void noiTiep() {
        assertTrue(timing.isOpen(1, START));
        assertTrue(timing.isOpen(1, START.plusMinutes(19).plusSeconds(59)));
        assertFalse(timing.isOpen(1, START.plusMinutes(20)));
        assertTrue(timing.isOpen(2, START.plusMinutes(20)));
        assertFalse(timing.isOpen(2, START.plusMinutes(19)));
    }

    @Test
    @DisplayName("Phần chưa tới lượt và phần đã hết giờ có câu giải thích khác nhau")
    void lyDoKhacNhau() {
        LocalDateTime inPart1 = START.plusMinutes(5);
        assertTrue(timing.closedReason(2, inPart1).contains("chưa tới lượt"));
        assertTrue(timing.closedReason(1, START.plusMinutes(25)).contains("hết giờ"));
    }

    @Test
    @DisplayName("Giây đầu tiên: StartedAt bị MySQL làm tròn LÊN, câu trả lời đầu vẫn phải được nhận")
    void giayDauTien() {
        // Lỗi có thật, bắt được khi chạy e2e.
        LocalDateTime justBefore = START.minusNanos(400_000_000);
        assertTrue(timing.isOpen(1, justBefore));
        assertFalse(timing.toViews(justBefore, Map.of(), Map.of()).get(0).isUpcoming());
        // Phần sau vẫn giữ nguyên luật: chưa tới giờ là chưa mở.
        assertFalse(timing.isOpen(2, justBefore));
        assertTrue(timing.toViews(justBefore, Map.of(), Map.of()).get(1).isUpcoming());
    }

    @Test
    @DisplayName("Câu không thuộc phần nào vẫn được làm — lỗi cấu hình đề không được làm thí sinh mất câu")
    void cauNgoaiPhan() {
        assertTrue(timing.isOpen(null, START.plusMinutes(80)));
        assertTrue(timing.isOpen(999, START.plusMinutes(80)));
    }

    @Test
    @DisplayName("Phần hiện hành và trạng thái hiển thị")
    void trangThai() {
        LocalDateTime t = START.plusMinutes(30);
        assertEquals(2, timing.currentSection(t).getSectionId());
        assertNull(timing.currentSection(START.plusMinutes(90)));

        List<ExamSectionView> views = timing.toViews(t, Map.of(1, 5, 2, 8), Map.of(1, 5));
        assertTrue(views.get(0).isLocked());
        assertTrue(views.get(1).isCurrent());
        assertEquals(30 * 60, views.get(1).getRemainingSeconds());
        assertTrue(views.get(2).isUpcoming());
        assertEquals(5, views.get(0).getAnsweredQuestions());
        assertEquals(START.plusMinutes(90), timing.endOfLastSection());
    }
}
