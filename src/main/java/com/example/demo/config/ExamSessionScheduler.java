package com.example.demo.config;

import com.example.demo.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Hai job nền của phiên thi.
 *
 * Vì sao cần job dù mọi request đã tự kiểm tra hết giờ: học sinh đóng laptop
 * hoặc mất mạng luôn cho tới hết giờ thì không còn request nào để kích hoạt
 * việc nộp bài. Không có job này, phiên đó nằm mãi ở IN_PROGRESS và giáo viên
 * không bao giờ thấy điểm.
 *
 * Job chỉ là lưới an toàn, không phải đường chính: bài vẫn được nộp ngay ở
 * request đầu tiên sau khi hết giờ, nên độ trễ của job không ảnh hưởng tới học
 * sinh còn online.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ExamSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionScheduler.class);

    private final SubmissionService submissionService;

    /**
     * Ngưỡng im lặng để coi là mất kết nối. Nên đặt gấp 2-3 lần chu kỳ heartbeat
     * của client (15-30 giây) để một nhịp bị trượt không lập tức báo động.
     */
    @Value("${exam.session.at-risk-after-seconds:90}")
    private long atRiskAfterSeconds;

    /** Quét các phiên đã quá ExpiresAt và nộp hộ. */
    @Scheduled(fixedDelayString = "${exam.session.auto-submit-interval-ms:30000}")
    public void autoSubmitExpired() {
        try {
            submissionService.autoSubmitExpiredSessions();
        } catch (Exception e) {
            // Không để job chết: lần quét sau vẫn phải chạy.
            log.error("Job tự động nộp bài quá giờ lỗi", e);
        }
    }

    /** Bật AtRiskStatus cho các phiên mất heartbeat quá lâu. */
    @Scheduled(fixedDelayString = "${exam.session.at-risk-scan-interval-ms:30000}")
    public void flagDisconnected() {
        try {
            submissionService.flagDisconnectedSessions(atRiskAfterSeconds);
        } catch (Exception e) {
            log.error("Job phát hiện học sinh mất kết nối lỗi", e);
        }
    }
}
