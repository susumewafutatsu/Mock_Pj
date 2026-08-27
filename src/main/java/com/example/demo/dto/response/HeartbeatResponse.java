package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Trả lời cho nhịp heartbeat 15-30 giây của client.
 *
 * Mục đích duy nhất là cập nhật LastActiveAt để phát hiện học sinh rớt mạng.
 * Nó KHÔNG gia hạn thêm giờ làm bài: {@code expiresAt} trả về đây luôn là mốc
 * đã chốt lúc bắt đầu thi.
 */
@Data
@Builder
public class HeartbeatResponse {

    private Integer submissionId;
    private SubmissionStatus status;
    private LocalDateTime serverTime;
    private LocalDateTime expiresAt;
    private long remainingSeconds;

    /** Trước nhịp này server đang coi học sinh là mất kết nối. */
    private boolean recoveredFromAtRisk;

    /**
     * true khi phiên đã hết giờ và vừa được nộp tự động. Client thấy cờ này thì
     * dừng bài thi và chuyển sang trang kết quả.
     */
    private boolean autoSubmitted;
}
