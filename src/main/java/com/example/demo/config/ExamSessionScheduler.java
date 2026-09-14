package com.example.demo.config;

import com.example.demo.repository.UserCardStateRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.NotificationService;
import com.example.demo.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;

/** Các job nền. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ExamSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionScheduler.class);

    /** Khoá ngắn hơn chu kỳ 30 giây một chút: node chết thì lượt sau node khác vẫn chạy được. */
    private static final Duration SESSION_JOB_TTL = Duration.ofSeconds(25);

    private final SubmissionService submissionService;
    private final SchedulerLock schedulerLock;
    private final NotificationService notificationService;
    private final com.example.demo.service.SrsService srsService;
    private final UserRepository userRepository;
    private final com.example.demo.service.RoomService roomService;

    /** Ngưỡng im lặng để coi là mất kết nối. */
    @Value("${exam.session.at-risk-after-seconds:90}")
    private long atRiskAfterSeconds;

    /** Quét các phiên đã quá ExpiresAt và nộp hộ. */
    @Scheduled(fixedDelayString = "${exam.session.auto-submit-interval-ms:30000}")
    public void autoSubmitExpired() {
        try {
            schedulerLock.runExclusively("exam-auto-submit", SESSION_JOB_TTL,
                    submissionService::autoSubmitExpiredSessions);
        } catch (Exception e) {
            // Không để job chết: lần quét sau vẫn phải chạy.
            log.error("Job tự động nộp bài quá giờ lỗi", e);
        }
    }

    /** Bật AtRiskStatus cho các phiên mất heartbeat quá lâu. */
    @Scheduled(fixedDelayString = "${exam.session.at-risk-scan-interval-ms:30000}")
    public void flagDisconnected() {
        try {
            schedulerLock.runExclusively("exam-at-risk-scan", SESSION_JOB_TTL,
                    () -> submissionService.flagDisconnectedSessions(atRiskAfterSeconds));
        } catch (Exception e) {
            log.error("Job phát hiện thí sinh mất kết nối lỗi", e);
        }
    }

    /** Thông báo nhắc / bắt đầu / kết thúc cho phòng thi hẹn giờ. */
    @Scheduled(fixedDelayString = "${room.notice.interval-ms:30000}")
    public void notifyScheduledRooms() {
        try {
            schedulerLock.runExclusively("room-scheduled-notice", SESSION_JOB_TTL, () -> {
                int sent = roomService.notifyScheduledRooms();
                if (sent > 0) {
                    log.info("Thông báo phòng thi hẹn giờ: gửi {} lượt", sent);
                }
            });
        } catch (Exception e) {
            log.error("Job thông báo phòng thi hẹn giờ lỗi", e);
        }
    }

    /** Nhắc ôn thẻ đến hạn, mỗi ngày một lần. */
    @Scheduled(cron = "${study.reminder.cron:0 0 8 * * *}")
    public void remindDueCards() {
        try {
            schedulerLock.runExclusively("study-due-reminder", Duration.ofMinutes(10), () -> {
                int sent = 0;
                for (var row : srsService.cardsAvailableTodayByUser().entrySet()) {
                    String userId = row.getKey();
                    long due = row.getValue();
                    if (due <= 0 || notificationService.sentToday(userId, NotificationService.Kind.CARDS_DUE)) {
                        continue;
                    }
                    var user = userRepository.findById(userId).orElse(null);
                    if (user == null || user.isLocked()) {
                        continue;
                    }
                    notificationService.notify(user, NotificationService.Kind.CARDS_DUE,
                            "Có " + due + " thẻ đến hạn ôn hôm nay",
                            "Ôn đúng hạn thì nhớ lâu hơn nhiều so với ôn dồn. Dành vài phút cho "
                                    + due + " thẻ đang chờ bạn.",
                            "/student/exams?tab=flashcards");
                    sent++;
                }
                log.info("Nhắc ôn thẻ đến hạn: gửi {} thông báo", sent);
            });
        } catch (Exception e) {
            log.error("Job nhắc ôn thẻ đến hạn lỗi", e);
        }
    }
}
