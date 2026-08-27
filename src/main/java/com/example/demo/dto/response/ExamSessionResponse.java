package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trạng thái đầy đủ của một phiên làm bài. Trả về cho cả lúc bắt đầu thi và
 * lúc quay lại sau khi mất kết nối — hai luồng dùng cùng một payload, nên
 * client không cần code riêng cho trường hợp "vào lại".
 *
 * Về đồng hồ: client đếm ngược theo {@code remainingSeconds}, và có thể tự
 * hiệu chỉnh lệch giờ bằng cặp {@code serverTime} / {@code expiresAt}. Không
 * bao giờ tính thời gian còn lại từ giờ máy của học sinh.
 */
@Data
@Builder
public class ExamSessionResponse {

    private Integer submissionId;
    private Integer examId;
    private String examTitle;
    private Integer durationMinutes;

    private SubmissionStatus status;

    /** true nếu đây là lần gọi tạo phiên mới, false nếu là tiếp tục phiên đang dở. */
    private boolean resumed;

    private LocalDateTime startedAt;

    /** Deadline chốt phía server. Không đổi trong suốt phiên thi. */
    private LocalDateTime expiresAt;

    /** Giờ server tại thời điểm trả response — để client bù lệch đồng hồ. */
    private LocalDateTime serverTime;

    /** Thời gian còn lại theo giờ server, đơn vị giây. */
    private long remainingSeconds;

    /** Học sinh đang bị nghi mất kết nối (heartbeat trễ). */
    private boolean atRisk;

    private int totalQuestions;

    /** Số câu đã có đáp án lưu trên server. */
    private int answeredQuestions;

    private List<ExamQuestionView> questions;
}
