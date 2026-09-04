package com.example.demo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Giáo viên tạo / sửa một đề thi.
 *
 * Số câu hỏi KHÔNG nằm ở đây: câu hỏi được gắn riêng qua
 * {@code POST /api/teacher/exams/{examId}/questions} để mỗi câu có snapshot
 * nội dung tại thời điểm gắn.
 */
@Data
public class ExamCreateRequest {

    @NotBlank(message = "Tên đề thi không được để trống")
    @Size(max = 200, message = "Tên đề thi tối đa 200 ký tự")
    private String title;

    /** Để trống = đề luyện tập tự do, mọi học sinh đều thấy. */
    private Integer classId;

    @NotNull(message = "Trình độ không được để trống")
    private Integer levelId;

    @NotNull(message = "Thời gian làm bài không được để trống")
    @Min(value = 1, message = "Thời gian làm bài tối thiểu 1 phút")
    @Max(value = 300, message = "Thời gian làm bài tối đa 300 phút")
    private Integer durationMinutes;

    @NotNull(message = "Thời gian mở đề không được để trống")
    private LocalDateTime startTime;

    @NotNull(message = "Thời gian đóng đề không được để trống")
    private LocalDateTime endTime;

    /** Chế độ thi thích ứng (chọn câu theo năng lực). Mặc định tắt. */
    private Boolean adaptive = false;
}
