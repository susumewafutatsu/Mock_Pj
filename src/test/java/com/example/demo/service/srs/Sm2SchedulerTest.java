package com.example.demo.service.srs;

import com.example.demo.domain.enums.ReviewGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thuật toán giãn cách ôn tập.
 *
 * Test này tồn tại vì đây là chỗ hỏng mà không ai phát hiện ra: lịch ôn sai
 * không làm app văng, không làm request đỏ, nó chỉ khiến người học ôn quá dày
 * hoặc quá thưa — và tới lúc nhận ra thì đã mất vài tuần học. Không có cách
 * nào thấy được bằng mắt trên UI, nên phải chốt bằng test.
 *
 * Toàn bộ chạy không cần Spring, không cần database: {@link Sm2Scheduler} là
 * hàm thuần đúng vì lý do này.
 */
class Sm2SchedulerTest {

    private static final BigDecimal START_EASE = Sm2Scheduler.DEFAULT_EASE;

    @Test
    @DisplayName("Chuỗi nhớ tốt: khoảng cách giãn dần 1 → 6 → 15 ngày")
    void chuoiNhoTotThiKhoangCachGianDan() {
        // Lần đúng đầu tiên: gặp lại sau 1 ngày
        Sm2Scheduler.Outcome first =
                Sm2Scheduler.next(START_EASE, 0, 0, 0, ReviewGrade.GOOD);
        assertEquals(1, first.intervalDays());
        assertEquals(1, first.repetitions());

        // Lần thứ hai: mốc cố định 6 ngày của SM-2
        Sm2Scheduler.Outcome second = Sm2Scheduler.next(
                first.easeFactor(), first.intervalDays(), first.repetitions(), 0,
                ReviewGrade.GOOD);
        assertEquals(6, second.intervalDays());

        // Từ lần ba trở đi mới nhân với hệ số dễ: 6 × 2,50 = 15
        Sm2Scheduler.Outcome third = Sm2Scheduler.next(
                second.easeFactor(), second.intervalDays(), second.repetitions(), 0,
                ReviewGrade.GOOD);
        assertEquals(15, third.intervalDays());

        // "Bình thường" không làm thay đổi hệ số dễ — công thức gốc cho delta 0
        assertEquals(0, START_EASE.compareTo(third.easeFactor()));
    }

    @Test
    @DisplayName("Quên thì học lại từ đầu và quay lại ngay trong hôm nay")
    void quenThiResetVeTheMoi() {
        // Một thẻ đã ôn tốt 4 lần, khoảng cách đang là 30 ngày
        Sm2Scheduler.Outcome forgotten =
                Sm2Scheduler.next(START_EASE, 30, 4, 1, ReviewGrade.AGAIN);

        assertEquals(0, forgotten.repetitions(), "chuỗi đúng phải về 0");
        assertEquals(0, forgotten.intervalDays(),
                "khoảng cách 0 nghĩa là gặp lại ngay trong phiên hôm nay");
        assertEquals(2, forgotten.lapses(), "phải đếm thêm một lần quên");
        assertFalse(forgotten.isMature());
    }

    @Test
    @DisplayName("Thẻ hay quên bị kéo hệ số dễ xuống và không bao giờ dưới 1,30")
    void heSoDeCoSanVaKhongTutXuongVoHan() {
        BigDecimal ease = START_EASE;
        // Quên liên tiếp 20 lần: mỗi lần trừ 0,14 nên về lý thuyết sẽ âm
        for (int i = 0; i < 20; i++) {
            ease = Sm2Scheduler.next(ease, 0, 0, i, ReviewGrade.AGAIN).easeFactor();
        }
        assertEquals(0, Sm2Scheduler.MIN_EASE.compareTo(ease),
                "hệ số dễ phải dừng ở sàn 1,30, nếu không thẻ sẽ quay lại dày tới mức vô nghĩa");
    }

    @Test
    @DisplayName("Khó giãn chậm hơn Bình thường, Bình thường chậm hơn Dễ")
    void mucDoNhoQuyetDinhTocDoGian() {
        int current = 10;
        int repetitions = 3;

        int hard = Sm2Scheduler.next(START_EASE, current, repetitions, 0, ReviewGrade.HARD)
                .intervalDays();
        int good = Sm2Scheduler.next(START_EASE, current, repetitions, 0, ReviewGrade.GOOD)
                .intervalDays();
        int easy = Sm2Scheduler.next(START_EASE, current, repetitions, 0, ReviewGrade.EASY)
                .intervalDays();

        assertTrue(hard < good, "Khó phải giãn chậm hơn Bình thường");
        assertTrue(good < easy, "Dễ phải giãn nhanh hơn Bình thường");
        assertTrue(hard > current, "kể cả Khó cũng phải tiến lên, không được đứng yên");
    }

    @Test
    @DisplayName("Khoảng cách luôn tiến lên ít nhất một ngày, kể cả khi hệ số chạm sàn")
    void khoangCachKhongBaoGioDungYen() {
        // Hệ số sàn 1,30 với khoảng cách 1 ngày: 1 × 1,30 làm tròn vẫn ra 1,
        // tức thẻ sẽ kẹt ở 1 ngày mãi mãi nếu không có chốt chặn.
        Sm2Scheduler.Outcome outcome =
                Sm2Scheduler.next(Sm2Scheduler.MIN_EASE, 1, 5, 0, ReviewGrade.HARD);
        assertTrue(outcome.intervalDays() >= 2,
                "thẻ kẹt ở cùng một khoảng cách sẽ bị ôn lại tới vô tận");
    }

    @Test
    @DisplayName("Đủ 21 ngày thì thẻ được coi là đã vào trí nhớ dài hạn")
    void nguongThuocLa21Ngay() {
        assertFalse(Sm2Scheduler.next(START_EASE, 8, 3, 0, ReviewGrade.GOOD).isMature(),
                "8 × 2,50 = 20 ngày, chưa tới ngưỡng");
        assertTrue(Sm2Scheduler.next(START_EASE, 9, 3, 0, ReviewGrade.GOOD).isMature(),
                "9 × 2,50 = 23 ngày, đã qua ngưỡng");
    }
}
