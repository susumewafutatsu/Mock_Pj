package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một dòng trong danh sách đề thi của người ra đề. */
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
    @JsonProperty("isPublic")
    private boolean isPublic;

    /** Cùng lý do với isPublic: ghi rõ tên JSON để Jackson không bỏ tiền tố "is". */
    @JsonProperty("isPlacement")
    private boolean isPlacement;

    private boolean shuffleQuestions;
    private boolean shuffleOptions;

    /** Đề đang chấm theo thang quy đổi JLPT (đã áp cấu trúc phần thi chuẩn). */
    private boolean jlptScoring;

    /** Đề này đang được gắn vào bao nhiêu phòng. */
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
