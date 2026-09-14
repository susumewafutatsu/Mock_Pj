package com.example.demo.dto.response;

import com.example.demo.domain.enums.RoomPhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Bảng xếp hạng — của một phòng thi (mỗi đề trong phòng một bảng), hoặc của một đề tự do (một bảng). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardResponse {

    public enum Scope { ROOM, EXAM }

    private Scope scope;

    // Chỉ có khi scope = ROOM
    private Integer roomId;
    private String roomName;
    private RoomPhase phase;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** false = phòng còn đang thi, số liệu còn đổi (chỉ người ra đề thấy được bảng lúc này). */
    private boolean finalResults;

    private List<Board> boards;

    private LocalDateTime serverTime;

    /** Bảng của một đề. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Board {
        private Integer examId;
        private String examTitle;
        private BigDecimal maxScore;
        private int totalQuestions;
        private Integer durationMinutes;

        /** Phòng: số thí sinh trong phòng. Đề tự do: số người đã nộp ít nhất một lượt. */
        private int participants;
        private int submittedCount;
        private BigDecimal averageScore;
        private BigDecimal highestScore;

        /** Phòng: mọi thí sinh, kể cả chưa làm (đứng cuối, không có hạng). */
        private List<Row> rows;

        /** Dòng của người đang xem, null nếu họ chưa có bài. */
        private Row myRow;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        /** Hạng. null = chưa có bài đã nộp. */
        private Integer rank;
        private String fullName;
        /** Số ghế trong phòng. null ở đề tự do. */
        private Integer seatNo;
        private boolean me;

        /** GRADED | SUBMITTED | IN_PROGRESS | NOT_STARTED */
        private String status;
        private BigDecimal score;
        /** Phần trăm điểm trên điểm tối đa, 0–100. */
        private Double percent;
        private Integer correctAnswers;
        private Long durationSeconds;
        private LocalDateTime submittedAt;
        private boolean autoSubmitted;
        /** Còn câu tự luận chờ chấm — điểm chưa phải điểm cuối. */
        private boolean awaitingManualGrading;

        /** Chỉ trả cho chính chủ bài và người ra đề, để mở trang xem lại bài. */
        private Integer submissionId;
    }
}
