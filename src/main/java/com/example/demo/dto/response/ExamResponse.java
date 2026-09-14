package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng trong danh sách đề thi của thí sinh. */
@Data
@Builder(toBuilder = true)
public class ExamResponse {

    /** Đề này với thí sinh đang đăng nhập thì đang ở trạng thái nào. */
    /** Đề này đến với thí sinh theo đường nào. */
    public enum Source {
        /** Đề của một phòng thí sinh đang tham gia — bài được giao. */
        ROOM,
        /** Đề công khai, thí sinh tự chọn làm. */
        PRACTICE
    }

    public enum Availability {
        /** Chưa tới giờ mở đề. */
        UPCOMING,
        /** Đang mở và thí sinh chưa bắt đầu — vào được ngay. */
        OPEN,
        /** Có phiên đang làm dở, còn giờ — vào lại để tiếp tục. */
        IN_PROGRESS,
        /** Đã nộp và vẫn còn lượt làm lại — hiện nút "Làm lại" bên cạnh "Xem lại bài". */
        RETAKEABLE,
        /** Đã nộp, không còn lượt (hoặc đề đã đóng). Chỉ xem được kết quả. */
        SUBMITTED,
        /** Đề đã đóng mà thí sinh không làm. Không vào được nữa. */
        CLOSED,
        /** Người ra đề chưa gắn câu hỏi nào — chưa thể bắt đầu. */
        NO_QUESTIONS,
        /** Đề của phòng thi đang ở sảnh chờ. */
        WAITING_ROOM
    }

    /** Pha của phòng mà dòng này thuộc về. */
    private com.example.demo.domain.enums.RoomPhase roomPhase;

    /** Giờ bắt đầu làm bài của phòng (hẹn trước, hoặc lúc người ra đề bấm). */
    private LocalDateTime roomStartTime;

    /** Giờ phòng hết giờ làm bài — sau mốc này thí sinh xem được bảng xếp hạng. */
    private LocalDateTime roomEndTime;

    private Integer examId;
    private String title;
    private Integer durationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean adaptive;
    private int totalQuestions;

    /** ROOM hay PRACTICE. Không bao giờ null. */
    private Source source;

    // Cố ý KHÔNG có roomId/roomName ở đây.

    /** Trình độ / môn học — client dùng làm bộ lọc ở trang đề luyện tập. */
    private Integer levelId;
    private String levelName;
    private Integer subjectId;
    private String subjectName;

    private String teacherName;

    // ── Trạng thái riêng của thí sinh đang đăng nhập ───────────────────────

    private Availability availability;

    /** Số lượt tối đa mỗi thí sinh được làm. null = không giới hạn. */
    private Integer maxAttempts;

    /** Số lượt thí sinh đang đăng nhập đã dùng trên đề này. */
    private long attemptsUsed;

    /** Số lượt còn lại. null khi đề không giới hạn — client hiện "∞" chứ không hiện 0. */
    private Integer attemptsRemaining;

    /** Bài đã nộp rồi có làm lại được không. */
    private boolean canRetake;

    /** Bài làm gần nhất — null nếu thí sinh chưa từng bắt đầu đề này. */
    private Integer submissionId;

    private SubmissionStatus submissionStatus;

    /** Deadline của phiên đang làm dở. null nếu chưa bắt đầu. */
    private LocalDateTime expiresAt;

    /** Thời gian còn lại của phiên đang làm dở, theo giờ server. 0 nếu không có phiên. */
    private long remainingSeconds;

    /** Điểm đã chấm, chỉ có khi đã nộp bài. */
    private BigDecimal totalScore;

    private LocalDateTime submittedAt;

    /** Giờ server lúc trả response — để client bù lệch đồng hồ. */
    private LocalDateTime serverTime;
}
