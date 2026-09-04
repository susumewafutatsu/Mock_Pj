package com.example.demo.dto.response;

import com.example.demo.domain.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách đề thi của học sinh.
 *
 * DTO này KHÔNG mang câu hỏi — đề chỉ được mở ra ở
 * {@code POST /api/student/exams/{id}/start} (xem {@link ExamSessionResponse}),
 * nên danh sách có thể tải công khai trong trang mà không lộ nội dung đề.
 *
 * Ngoài thông tin của đề, mỗi dòng còn kèm trạng thái riêng của học sinh đang
 * đăng nhập ({@link #availability}) để client biết nên hiện nút "Làm bài",
 * "Tiếp tục" hay "Xem kết quả" mà không phải tự suy ra từ mốc thời gian.
 */
@Data
@Builder
public class ExamResponse {

    /**
     * Đề này với học sinh đang đăng nhập thì đang ở trạng thái nào.
     *
     * Được tính bằng giờ server, nên client không cần so sánh startTime /
     * endTime với đồng hồ máy học sinh nữa.
     */
    /**
     * Đề này đến với học sinh theo đường nào.
     *
     * Trước đây client phải suy ra bằng {@code className == null}, tức là dựa
     * vào một trường có thể null vì lý do khác (dữ liệu cũ, lớp bị xoá) để
     * quyết định hiển thị. Giờ nó là một giá trị được server nói thẳng.
     */
    public enum Source {
        /** Đề của một lớp học sinh đang học — bài giáo viên giao. */
        CLASS,
        /** Đề luyện tập tự do, học sinh tự chọn làm. */
        PRACTICE
    }

    public enum Availability {
        /** Chưa tới giờ mở đề. */
        UPCOMING,
        /** Đang mở và học sinh chưa bắt đầu — vào được ngay. */
        OPEN,
        /** Có phiên đang làm dở, còn giờ — vào lại để tiếp tục. */
        IN_PROGRESS,
        /** Đã nộp (tự nộp hay học sinh bấm nộp). Chỉ xem được kết quả. */
        SUBMITTED,
        /** Đề đã đóng mà học sinh không làm. Không vào được nữa. */
        CLOSED,
        /** Giáo viên chưa gắn câu hỏi nào — chưa thể bắt đầu. */
        NO_QUESTIONS
    }

    private Integer examId;
    private String title;
    private Integer durationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean adaptive;
    private int totalQuestions;

    /** CLASS hay PRACTICE. Không bao giờ null. */
    private Source source;

    /** null khi source là PRACTICE. Có để client mở được trang lớp tương ứng. */
    private Integer classId;
    private String className;

    /** Trình độ / môn học — client dùng làm bộ lọc ở trang đề luyện tập. */
    private Integer levelId;
    private String levelName;
    private Integer subjectId;
    private String subjectName;

    private String teacherName;

    // ── Trạng thái riêng của học sinh đang đăng nhập ───────────────────────

    private Availability availability;

    /** null nếu học sinh chưa từng bắt đầu đề này. */
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
