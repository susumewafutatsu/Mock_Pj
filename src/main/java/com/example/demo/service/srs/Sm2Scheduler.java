package com.example.demo.service.srs;

import com.example.demo.domain.enums.ReviewGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Thuật toán lặp lại ngắt quãng SM-2 — trái tim của việc học thuộc.
 *
 * Ý tưởng: khoảng cách giữa hai lần ôn một thẻ nên giãn dần theo cấp số nhân,
 * và tốc độ giãn phụ thuộc vào việc người học thấy thẻ đó dễ hay khó. Nhờ vậy
 * mỗi ngày chỉ phải ôn vài chục thẻ sắp quên thay vì đọc lại cả nghìn thẻ.
 *
 * Lớp này cố tình là hàm thuần: vào là trạng thái cũ + đánh giá, ra là trạng
 * thái mới, không chạm database, không đọc đồng hồ. Toàn bộ phần dễ sai của
 * nghiệp vụ học tập nằm gọn ở đây và test được trực tiếp không cần Spring.
 *
 * Công thức gốc (Wozniak, 1987), giữ nguyên:
 *   EF' = EF + (0,1 − (5−q) × (0,08 + (5−q) × 0,02)),  EF không xuống dưới 1,30
 *   q < 3            -> quên: repetitions = 0, ôn lại ngay trong ngày
 *   repetitions = 1  -> 1 ngày
 *   repetitions = 2  -> 6 ngày
 *   về sau           -> khoảng cách trước × EF'
 */
public final class Sm2Scheduler {

    /** Hệ số dễ khởi điểm của một thẻ mới. */
    public static final BigDecimal DEFAULT_EASE = new BigDecimal("2.50");

    /** Sàn hệ số dễ. Dưới mức này thẻ sẽ quay lại quá dày và người học kiệt sức. */
    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

    /** Khoảng cách sau lần ôn đúng đầu tiên. */
    private static final int FIRST_INTERVAL_DAYS = 1;

    /** Khoảng cách sau lần ôn đúng thứ hai. */
    private static final int SECOND_INTERVAL_DAYS = 6;

    /**
     * Thẻ đạt khoảng cách này trở lên thì coi như đã thuộc.
     * 21 ngày là ngưỡng quen dùng: nhớ được sau ba tuần thì kiến thức đã
     * chuyển sang trí nhớ dài hạn chứ không còn là thuộc lòng tạm thời.
     */
    public static final int MATURE_INTERVAL_DAYS = 21;

    /**
     * Trả lời "Khó" vẫn tính là đúng nhưng không nên nới rộng như "Bình thường".
     * Nhân 1,2 thay vì nhân nguyên hệ số dễ — đây là phần bổ sung ngoài SM-2
     * gốc, lấy theo cách Anki xử lý, vì SM-2 gốc chỉ có một đường cho mọi mức đúng.
     */
    private static final BigDecimal HARD_MULTIPLIER = new BigDecimal("1.2");

    private Sm2Scheduler() {
    }

    /** Trạng thái mới của thẻ sau một lần ôn. */
    public record Outcome(BigDecimal easeFactor, int intervalDays, int repetitions, int lapses) {

        /** Thẻ đã bước vào trí nhớ dài hạn chưa. */
        public boolean isMature() {
            return intervalDays >= MATURE_INTERVAL_DAYS;
        }
    }

    /**
     * Tính trạng thái kế tiếp của một thẻ.
     *
     * @param easeFactor  hệ số dễ hiện tại; null thì lấy mặc định
     * @param intervalDays khoảng cách hiện tại tính bằng ngày, 0 nếu là thẻ mới
     * @param repetitions số lần ôn đúng liên tiếp trước lần này
     * @param lapses      số lần đã quên trước lần này
     * @param grade       người học vừa tự đánh giá thế nào
     */
    public static Outcome next(BigDecimal easeFactor, int intervalDays,
                               int repetitions, int lapses, ReviewGrade grade) {
        BigDecimal ease = easeFactor == null ? DEFAULT_EASE : easeFactor;
        int quality = grade.quality();

        // Hệ số dễ được cập nhật ở MỌI lần ôn, kể cả lần quên: một thẻ hay bị
        // quên phải trở nên "khó" vĩnh viễn, nếu không nó sẽ lại giãn ra nhanh
        // như cũ ngay sau lần đúng kế tiếp.
        BigDecimal delta = deltaFor(quality);
        BigDecimal newEase = ease.add(delta).setScale(2, RoundingMode.HALF_UP);
        if (newEase.compareTo(MIN_EASE) < 0) {
            newEase = MIN_EASE;
        }

        if (grade.isForgotten()) {
            // Quên thì học lại từ đầu, và xếp ôn ngay trong hôm nay
            // (intervalDays = 0) chứ không đợi sang ngày mai — lúc vừa nhìn
            // thấy đáp án là lúc dễ ghi nhớ nhất.
            return new Outcome(newEase, 0, 0, lapses + 1);
        }

        int newRepetitions = repetitions + 1;
        int newInterval = switch (newRepetitions) {
            case 1 -> FIRST_INTERVAL_DAYS;
            case 2 -> SECOND_INTERVAL_DAYS;
            default -> grow(intervalDays, newEase, grade);
        };

        return new Outcome(newEase, newInterval, newRepetitions, lapses);
    }

    /** Khoảng cách kế tiếp cho thẻ đã qua hai lần ôn đúng. */
    private static int grow(int intervalDays, BigDecimal ease, ReviewGrade grade) {
        // Thẻ lẽ ra phải có khoảng cách nhưng lại đang là 0 (dữ liệu cũ, hoặc
        // vừa được reset): lấy 1 ngày làm mốc để phép nhân có ý nghĩa.
        int base = Math.max(intervalDays, FIRST_INTERVAL_DAYS);
        BigDecimal multiplier = grade == ReviewGrade.HARD ? HARD_MULTIPLIER : ease;
        int grown = BigDecimal.valueOf(base)
                .multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        // Luôn phải tiến lên ít nhất một ngày, nếu không thẻ đứng yên mãi ở
        // cùng một khoảng cách và người học ôn lại nó tới vô tận.
        return Math.max(grown, base + 1);
    }

    /** Phần thay đổi hệ số dễ theo công thức gốc của SM-2. */
    private static BigDecimal deltaFor(int quality) {
        double gap = 5.0 - quality;
        double delta = 0.1 - gap * (0.08 + gap * 0.02);
        return BigDecimal.valueOf(delta).setScale(2, RoundingMode.HALF_UP);
    }
}
