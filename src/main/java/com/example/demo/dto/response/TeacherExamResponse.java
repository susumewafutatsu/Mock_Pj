package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách đề thi của giáo viên.
 *
 * Khác {@link ExamResponse} (phía học sinh): ở đây không có trạng thái riêng
 * của từng học sinh, thay vào đó là số liệu quản lý — đã gắn bao nhiêu câu, bao
 * nhiêu em đã nộp trên tổng sĩ số lớp.
 */
@Data
@Builder
public class TeacherExamResponse {

    /** Trạng thái đề theo giờ server, để client không phải tự so mốc thời gian. */
    public enum Status {
        /** Chưa gắn câu hỏi nào — học sinh chưa vào thi được. */
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

    /** null nếu là đề luyện tập tự do, không gắn lớp. */
    private Integer classId;
    private String className;

    private Integer levelId;
    private String levelName;
    private String subjectName;

    private int totalQuestions;

    /** Số học sinh đã có phiên làm bài (kể cả đang làm dở). */
    private long submissionCount;

    /** Sĩ số lớp. 0 với đề luyện tập tự do. */
    private long totalStudents;

    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime serverTime;
}
