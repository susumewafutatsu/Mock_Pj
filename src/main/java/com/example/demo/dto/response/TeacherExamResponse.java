package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách đề thi của người ra đề.
 *
 * Khác {@link ExamResponse} (phía thí sinh): ở đây không có trạng thái riêng
 * của từng thí sinh, thay vào đó là số liệu quản lý — đã gắn bao nhiêu câu, bao
 * nhiêu người đã nộp trên tổng số thí sinh trong các phòng chứa đề.
 */
@Data
@Builder
public class TeacherExamResponse {

    /** Trạng thái đề theo giờ server, để client không phải tự so mốc thời gian. */
    public enum Status {
        /** Chưa gắn câu hỏi nào — thí sinh chưa vào thi được. */
        NO_QUESTIONS,
        /** Chưa tới giờ mở đề. */
        UPCOMING,
        /** Đang trong khoảng mở đề. */
        OPEN,
        /** Đã qua giờ đóng đề. */
        CLOSED
    }

    private Integer examId;
    private String title;
    private Integer durationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean adaptive;

    /** Số lượt mỗi thí sinh được làm. null = không giới hạn. */
    private Integer maxAttempts;

    /** Thí sinh được xem đáp án đúng + giải thích sau khi nộp. */
    private boolean allowReview;

    /** Đề công khai — mọi thí sinh làm được, không cần vào phòng nào. */
    private boolean isPublic;

    /**
     * Đề này đang được gắn vào bao nhiêu phòng.
     *
     * Thay cho cặp {@code classId}/{@code className} cũ: một đề giờ dùng lại
     * được ở nhiều phòng, nên một cái tên lớp duy nhất không còn diễn tả nổi.
     */
    private long roomCount;

    private Integer levelId;
    private String levelName;
    private String subjectName;

    private int totalQuestions;

    /** Số thí sinh đã có phiên làm bài (kể cả đang làm dở). */
    private long submissionCount;

    /** Tổng số thí sinh trong các phòng chứa đề. 0 với đề công khai. */
    /** Tổng số thí sinh đang ở trong các phòng có chứa đề này. */
    private long totalCandidates;

    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime serverTime;
}
