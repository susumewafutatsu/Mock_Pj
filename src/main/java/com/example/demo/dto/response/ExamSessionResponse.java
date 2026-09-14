package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Trạng thái đầy đủ của một phiên làm bài. */
@Data
@Builder
public class ExamSessionResponse {

    private Integer submissionId;
    private Integer examId;
    private String examTitle;
    private Integer durationMinutes;

    /** Lượt làm thứ mấy, đếm từ 1 — phòng thi hiện "Lần 2/3" trên thanh tiêu đề. */
    private Integer attemptNumber;

    /** Số lượt tối đa của đề. null = không giới hạn. */
    private Integer maxAttempts;

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

    /** Thí sinh đang bị nghi mất kết nối (heartbeat trễ). */
    private boolean atRisk;

    private int totalQuestions;

    /** Số câu đã có đáp án lưu trên server. */
    private int answeredQuestions;

    private List<ExamQuestionView> questions;

    /** Các phần thi, theo thứ tự. */
    private List<ExamSectionView> sections;
}
