package com.example.demo.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vòng đời một câu trong sổ tay câu sai.
 *
 * Chốt bằng test vì luật ở đây dễ bị hiểu nhầm thành "đúng một lần là xong":
 * nhớ được một lần không phải là thuộc, và một câu đã thuộc mà sai lại thì
 * phải quay về hàng đợi chứ không được ở yên trong danh sách đã xong.
 */
class MistakeEntryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 9, 0);

    private MistakeEntry newEntry() {
        return MistakeEntry.builder().wrongCount(0).correctStreak(0).build();
    }

    @Test
    @DisplayName("Sai lần đầu thì vào sổ tay và tới hạn ôn ngay")
    void saiLanDauThiVaoSoTayVaToiHanNgay() {
        MistakeEntry entry = newEntry();
        entry.recordWrong(NOW);

        assertEquals(1, entry.getWrongCount());
        assertEquals(0, entry.getCorrectStreak());
        assertFalse(entry.isMastered());
        assertTrue(entry.isDueAt(NOW),
                "vừa sai xong mà hẹn vài ngày nữa mới ôn thì lúc ôn đã quên hết bối cảnh");
    }

    @Test
    @DisplayName("Phải đúng hai lần liên tiếp mới được coi là đã sửa xong")
    void dungMotLanChuaDuDeCoiLaThuoc() {
        MistakeEntry entry = newEntry();
        entry.recordWrong(NOW);

        boolean masteredAfterFirst = entry.recordCorrect(NOW);
        assertFalse(masteredAfterFirst, "đúng một lần có thể chỉ là đoán trúng");
        assertEquals(1, entry.getCorrectStreak());
        assertFalse(entry.isMastered());
        // Lần đúng đầu tiên hẹn lại sau 1 ngày, nên chưa tới hạn ngay
        assertFalse(entry.isDueAt(NOW));
        assertEquals(NOW.plusDays(1), entry.getNextReviewAt());

        boolean masteredAfterSecond = entry.recordCorrect(NOW.plusDays(1));
        assertTrue(masteredAfterSecond);
        assertTrue(entry.isMastered());
        assertNull(entry.getNextReviewAt(), "đã sửa xong thì không còn lịch ôn nữa");
        assertFalse(entry.isDueAt(NOW.plusYears(1)),
                "câu đã sửa xong không được xuất hiện lại trong hàng đợi");
    }

    @Test
    @DisplayName("Sai lại sau khi đã thuộc thì câu quay về hàng đợi")
    void saiLaiSauKhiDaThuocThiMoKhoaTroLai() {
        MistakeEntry entry = newEntry();
        entry.recordWrong(NOW);
        entry.recordCorrect(NOW);
        entry.recordCorrect(NOW.plusDays(1));
        assertTrue(entry.isMastered());

        entry.recordWrong(NOW.plusDays(30));

        assertFalse(entry.isMastered(), "nhớ được một lần không có nghĩa là nhớ mãi");
        assertEquals(0, entry.getCorrectStreak(), "chuỗi đúng phải đếm lại từ đầu");
        assertEquals(2, entry.getWrongCount());
        assertTrue(entry.isDueAt(NOW.plusDays(30)));
    }

    @Test
    @DisplayName("Chưa có lịch ôn thì coi như tới hạn")
    void chuaCoLichThiCoiNhuToiHan() {
        MistakeEntry entry = newEntry();
        assertNull(entry.getNextReviewAt());
        assertTrue(entry.isDueAt(NOW),
                "dữ liệu cũ chưa có lịch không được biến mất khỏi sổ tay");
    }
}
