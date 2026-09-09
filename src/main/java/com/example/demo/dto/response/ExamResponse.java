package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách đề thi của thí sinh.
 *
 * DTO này KHÔNG mang câu hỏi — đề chỉ được mở ra ở
 * {@code POST /api/student/exams/{id}/start} (xem {@link ExamSessionResponse}),
 * nên danh sách có thể tải công khai trong trang mà không lộ nội dung đề.
 *
 * Ngoài thông tin của đề, mỗi dòng còn kèm trạng thái riêng của thí sinh đang
 * đăng nhập ({@link #availability}) để client biết nên hiện nút "Làm bài",
 * "Tiếp tục" hay "Xem kết quả" mà không phải tự suy ra từ mốc thời gian.
 */
@Data
@Builder
public class ExamResponse {

    /**
     * Đề này với thí sinh đang đăng nhập thì đang ở trạng thái nào.
     *
     * Được tính bằng giờ server, nên client không cần so sánh startTime /
     * endTime với đồng hồ máy thí sinh nữa.
     */
    /**
     * Đề này đến với thí sinh theo đường nào.
     *
     * Trước đây client phải suy ra bằng {@code className == null}, tức là dựa
     * vào một trường có thể null vì lý do khác (dữ liệu cũ, lớp bị xoá) để
     * quyết định hiển thị. Giờ nó là một giá trị được server nói thẳng.
     */
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
        /**
         * Đã nộp và vẫn còn lượt làm lại — hiện nút "Làm lại" bên cạnh "Xem lại bài".
         * Chỉ xuất hiện với đề còn trong giờ mở.
         */
        RETAKEABLE,
        /** Đã nộp, không còn lượt (hoặc đề đã đóng). Chỉ xem được kết quả. */
        SUBMITTED,
        /** Đề đã đóng mà thí sinh không làm. Không vào được nữa. */
        CLOSED,
        /** Người ra đề chưa gắn câu hỏi nào — chưa thể bắt đầu. */
        NO_QUESTIONS
    }

    private Integer examId;
    private String title;
    private Integer durationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean adaptive;
    private int totalQuestions;

    /** ROOM hay PRACTICE. Không bao giờ null. */
    private Source source;

    // Cố ý KHÔNG có roomId/roomName ở đây. Thời còn lớp, một đề thuộc đúng một
    // lớp nên nhét được cặp id/tên vào đây là hợp lý. Giờ một đề gắn được vào
    // nhiều phòng, nên một cặp duy nhất sẽ là nói dối — phòng nào chứa đề nào
    // đã do RoomExamGroup nói rõ rồi.

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

    /**
     * Bài đã nộp rồi có làm lại được không.
     *
     * Không suy được từ {@code attemptsRemaining} một mình: còn lượt nhưng đề đã
     * đóng thì vẫn không vào được. Server hợp nhất hai điều kiện đó ở đây.
     */
    private boolean canRetake;

    /**
     * Bài làm gần nhất — null nếu thí sinh chưa từng bắt đầu đề này.
     * Với đề nhiều lượt, đây là lượt mới nhất, không phải lượt điểm cao nhất.
     */
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
