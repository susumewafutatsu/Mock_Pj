package com.example.demo.dto.response;

import com.example.demo.domain.enums.RoomPhase;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Theo dõi trực tiếp một phòng thi (chỉ chủ phòng). */
@Data
@Builder
public class RoomMonitorResponse {

    private Integer roomId;
    private RoomPhase phase;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime lateJoinUntil;
    private LocalDateTime serverTime;

    private Integer examId;
    private String examTitle;
    private int totalQuestions;
    private BigDecimal maxScore;

    private int joined;
    private int online;
    private int notStarted;
    private int inProgress;
    private int submitted;
    private int atRisk;

    private List<Row> members;

    @Data
    @Builder
    public static class Row {
        private String userId;
        private String fullName;
        private String email;
        private Integer seatNo;
        private LocalDateTime joinedAt;

        /** Đang mở trang phòng hoặc đang làm bài. */
        private boolean online;
        private LocalDateTime lastSeenAt;

        /** NOT_STARTED | IN_PROGRESS | SUBMITTED */
        private String status;

        private int answered;
        private boolean atRisk;
        private LocalDateTime startedAt;
        private LocalDateTime submittedAt;
        private boolean autoSubmitted;

        private BigDecimal score;
        private Integer percent;
        private Integer submissionId;
    }
}
