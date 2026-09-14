package com.example.demo.service.srs;

import com.example.demo.domain.enums.ReviewGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/** Thuật toán lặp lại ngắt quãng SM-2, có phân biệt mức ngay từ lần ôn đầu. */
public final class Sm2Scheduler {

    /** Hệ số dễ khởi điểm của một thẻ mới. */
    public static final BigDecimal DEFAULT_EASE = new BigDecimal("2.50");

    /** Sàn hệ số dễ. */
    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

    /** Thẻ đạt khoảng cách này trở lên thì coi như đã thuộc. */
    public static final int MATURE_INTERVAL_DAYS = 21;

    private static final int FIRST_INTERVAL_DAYS = 1;
    private static final int FIRST_EASY_DAYS = 4;
    private static final int SECOND_INTERVAL_DAYS = 6;

    private static final BigDecimal HARD_MULTIPLIER = new BigDecimal("1.2");
    private static final BigDecimal EASY_BONUS = new BigDecimal("1.3");

    private Sm2Scheduler() {
    }

    /** Trạng thái mới của thẻ sau một lần ôn. */
    public record Outcome(BigDecimal easeFactor, int intervalDays, int repetitions, int lapses) {

        public boolean isMature() {
            return intervalDays >= MATURE_INTERVAL_DAYS;
        }
    }

    /** Tính trạng thái kế tiếp của một thẻ. */
    public static Outcome next(BigDecimal easeFactor, int intervalDays,
                               int repetitions, int lapses, ReviewGrade grade) {
        BigDecimal ease = easeFactor == null ? DEFAULT_EASE : easeFactor;
        BigDecimal newEase = ease.add(deltaFor(grade.quality())).setScale(2, RoundingMode.HALF_UP);
        if (newEase.compareTo(MIN_EASE) < 0) {
            newEase = MIN_EASE;
        }

        if (grade.isForgotten()) {
            // Quên: học lại từ đầu, gặp lại ngay trong phiên.
            return new Outcome(newEase, 0, 0, lapses + 1);
        }

        int newRepetitions = repetitions + 1;
        int newInterval = switch (newRepetitions) {
            case 1 -> grade == ReviewGrade.EASY ? FIRST_EASY_DAYS : FIRST_INTERVAL_DAYS;
            case 2 -> second(intervalDays, grade);
            default -> grow(intervalDays, newEase, grade);
        };
        return new Outcome(newEase, newInterval, newRepetitions, lapses);
    }

    /** Khoảng cách sẽ gặp lại cho từng mức, để hiện trên nút đánh giá. */
    public static Map<ReviewGrade, Integer> preview(BigDecimal easeFactor, int intervalDays,
                                                    int repetitions, int lapses) {
        Map<ReviewGrade, Integer> out = new EnumMap<>(ReviewGrade.class);
        for (ReviewGrade g : ReviewGrade.values()) {
            out.put(g, next(easeFactor, intervalDays, repetitions, lapses, g).intervalDays());
        }
        return out;
    }

    /** Lần ôn đúng thứ hai. */
    private static int second(int intervalDays, ReviewGrade grade) {
        int base = Math.max(intervalDays, FIRST_INTERVAL_DAYS);
        int good = Math.max(SECOND_INTERVAL_DAYS, base + 1);
        return switch (grade) {
            case HARD -> Math.max(base + 1, Math.round(good * 0.5f));
            case EASY -> Math.max(good + 1, scale(good, EASY_BONUS));
            default -> good;
        };
    }

    /** Từ lần ôn đúng thứ ba: nhân theo hệ số dễ. */
    private static int grow(int intervalDays, BigDecimal ease, ReviewGrade grade) {
        int base = Math.max(intervalDays, FIRST_INTERVAL_DAYS);
        int good = Math.max(base + 1, scale(base, ease));
        return switch (grade) {
            case HARD -> Math.max(base + 1, Math.min(scale(base, HARD_MULTIPLIER), good - 1));
            case EASY -> Math.max(good + 1, scale(base, ease.multiply(EASY_BONUS)));
            default -> good;
        };
    }

    private static int scale(int days, BigDecimal factor) {
        return BigDecimal.valueOf(days).multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** Thay đổi hệ số dễ theo công thức gốc SM-2. */
    private static BigDecimal deltaFor(int quality) {
        double gap = 5.0 - quality;
        double delta = 0.1 - gap * (0.08 + gap * 0.02);
        return BigDecimal.valueOf(delta).setScale(2, RoundingMode.HALF_UP);
    }
}
