package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Trả lời cho nhịp heartbeat 15-30 giây của client. */
@Data
@Builder
public class HeartbeatResponse {

    private Integer submissionId;
    private SubmissionStatus status;
    private LocalDateTime serverTime;
    private LocalDateTime expiresAt;
    private long remainingSeconds;

    /** Trước nhịp này server đang coi thí sinh là mất kết nối. */
    private boolean recoveredFromAtRisk;

    /** true khi phiên đã hết giờ và vừa được nộp tự động. */
    private boolean autoSubmitted;
}
