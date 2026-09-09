package com.example.demo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Người ra đề tạo / sửa một đề thi.
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

    /**
     * Đề công khai — mọi thí sinh đều thấy và làm được, không cần vào phòng nào.
     *
     * Thay cho {@code classId} thời còn lớp học. Đề không công khai thì chỉ tới
     * được với thí sinh qua việc gắn vào phòng thi (POST /api/rooms/{id}/exams/{examId}),
     * và một đề gắn được vào nhiều phòng — nên nơi gắn không còn thuộc về việc
     * TẠO đề nữa.
     */
    private Boolean isPublic = false;

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

    /**
     * Số lượt mỗi thí sinh được làm đề này. Để trống = không giới hạn.
     *
     * Đề luyện tập thường để trống — làm đi làm lại chính là mục đích. Bài kiểm
     * tra thì đặt 1. Trần 20 chỉ để chặn nhập nhầm; muốn nhiều hơn thì bỏ trống,
     * vì con số nào cũng không đúng bằng "không giới hạn".
     */
    @Min(value = 1, message = "Số lượt làm bài tối thiểu là 1")
    @Max(value = 20, message = "Số lượt làm bài tối đa là 20, để trống nếu muốn không giới hạn")
    private Integer maxAttempts;

    /**
     * Cho thí sinh xem đáp án đúng + giải thích sau khi nộp. Mặc định bật, vì
     * với đề ôn tập thì đó là phần thí sinh học được nhiều nhất.
     */
    private Boolean allowReview = true;
}
