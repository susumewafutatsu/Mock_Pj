package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Chặn gửi dồn ở những đường dễ bị lạm dụng nhất. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String pattern, boolean perUser, SlidingWindowRateLimiter limiter) {
    }

    private static final long MINUTE = 60_000L;

    private final List<Rule> rules = List.of(
            new Rule("POST", "/api/auth/login",    false, new SlidingWindowRateLimiter(10, MINUTE)),
            new Rule("POST", "/api/auth/register", false, new SlidingWindowRateLimiter(5, MINUTE)),
            new Rule("POST", "/api/auth/refresh",  false, new SlidingWindowRateLimiter(30, MINUTE)),
            new Rule("PUT",  "/api/student/exams/*/answers/batch", true, new SlidingWindowRateLimiter(120, MINUTE)),
            new Rule("PUT",  "/api/student/exams/*/answers",       true, new SlidingWindowRateLimiter(120, MINUTE)),
            new Rule("POST", "/api/student/exams/*/heartbeat",     true, new SlidingWindowRateLimiter(20, MINUTE)),
            new Rule("POST", "/api/student/exams/*/questions/*/audio-play", true, new SlidingWindowRateLimiter(30, MINUTE))
    );

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final AtomicLong requestCounter = new AtomicLong();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        long now = System.currentTimeMillis();

        // Thỉnh thoảng dọn khoá không còn hoạt động, khỏi cần thêm một job nền.
        if (requestCounter.incrementAndGet() % 1000 == 0) {
            rules.forEach(rule -> rule.limiter().evictIdle(now));
        }

        for (Rule rule : rules) {
            if (!rule.method().equalsIgnoreCase(method) || !matcher.match(rule.pattern(), path)) {
                continue;
            }
            String key = rule.perUser() ? userKey(request) : "ip:" + clientIp(request);
            if (!rule.limiter().tryAcquire(key, now)) {
                long retry = rule.limiter().retryAfterSeconds(key, now);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retry));
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":null,\"data\":null,"
                        + "\"error\":\"Bạn thao tác quá nhanh. Thử lại sau " + retry + " giây.\"}");
                return;
            }
            break;
        }
        chain.doFilter(request, response);
    }

    private String userKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        return "ip:" + clientIp(request);
    }

    /** IP của người gọi. Chỉ tin X-Forwarded-For khi có. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
