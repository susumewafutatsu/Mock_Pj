package com.example.demo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Giới hạn tần suất theo cửa sổ trượt. */
class SlidingWindowRateLimiterTest {

    @Test
    @DisplayName("Cho qua đúng giới hạn rồi chặn; mỗi khoá đếm riêng")
    void chanKhiVuot() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 60_000);
        assertTrue(limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("a", 1));
        assertTrue(limiter.tryAcquire("a", 2));
        assertFalse(limiter.tryAcquire("a", 3));
        assertTrue(limiter.tryAcquire("b", 3), "khoá khác không bị ảnh hưởng");
    }

    @Test
    @DisplayName("Cửa sổ TRƯỢT: không lọt được bằng cách dồn request quanh ranh giới phút")
    void cuaSoTruot() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, 60_000);
        assertTrue(limiter.tryAcquire("ip", 59_000));
        assertTrue(limiter.tryAcquire("ip", 59_500));
        // Cửa sổ cố định theo phút sẽ cho qua ở giây 61; cửa sổ trượt thì không.
        assertFalse(limiter.tryAcquire("ip", 61_000));
        assertTrue(limiter.retryAfterSeconds("ip", 61_000) > 0);
        assertTrue(limiter.tryAcquire("ip", 119_001));
    }

    @Test
    @DisplayName("Dọn khoá không còn hoạt động để bộ nhớ không phình")
    void donKhoaCu() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, 1_000);
        limiter.tryAcquire("x", 0);
        limiter.tryAcquire("y", 0);
        limiter.evictIdle(5_000);
        assertEquals(0, limiter.trackedKeys());
    }
}
