package com.example.demo.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Giới hạn số request trong một cửa sổ thời gian trượt, theo từng khoá. */
public final class SlidingWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    /** true = cho qua (và đã tính lượt này); false = vượt giới hạn. */
    public boolean tryAcquire(String key, long nowMillis) {
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            evict(deque, nowMillis);
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(nowMillis);
            return true;
        }
    }

    /** Số giây tới khi khoá này được gửi tiếp — cho header Retry-After. */
    public long retryAfterSeconds(String key, long nowMillis) {
        Deque<Long> deque = hits.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            evict(deque, nowMillis);
            Long oldest = deque.peekFirst();
            return oldest == null ? 0 : Math.max(1, (oldest + windowMillis - nowMillis + 999) / 1000);
        }
    }

    /** Dọn các khoá không còn lượt nào trong cửa sổ, để bộ nhớ không phình mãi. */
    public void evictIdle(long nowMillis) {
        hits.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                evict(deque, nowMillis);
                return deque.isEmpty();
            }
        });
    }

    int trackedKeys() {
        return hits.size();
    }

    private void evict(Deque<Long> deque, long nowMillis) {
        while (!deque.isEmpty() && deque.peekFirst() <= nowMillis - windowMillis) {
            deque.pollFirst();
        }
    }
}
