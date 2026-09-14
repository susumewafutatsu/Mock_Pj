package com.example.demo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** Người ra đề tạo / sửa một đề thi. */
@Data
public class ExamCreateRequest {

    @NotBlank(message = "Tên đề thi không được để trống")
    @Size(max = 200, message = "Tên đề thi tối đa 200 ký tự")
    private String title;

    /** Đề công khai — mọi thí sinh đều thấy và làm được, không cần vào phòng nào. */
    private Boolean isPublic = false;

    @NotNull(message = "Trình độ không được để trống")
    private Integer levelId;

    @NotNull(message = "Thời gian làm bài không được để trống")
    @Min(value = 1, message = "Thời gian làm bài tối thiểu 1 phút")
    @Max(value = 300, message = "Thời gian làm bài tối đa 300 phút")
    private Integer durationMinutes;

    /** Cửa sổ mở đề. ĐỂ TRỐNG ĐƯỢC, và với đề tự do thì để trống mới là cách dùng đúng. */
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** Chế độ thi thích ứng (chọn câu theo năng lực). Mặc định tắt. */
    private Boolean adaptive = false;

    /** Số lượt mỗi thí sinh được làm đề này. */
    @Min(value = 1, message = "Số lượt làm bài tối thiểu là 1")
    @Max(value = 20, message = "Số lượt làm bài tối đa là 20, để trống nếu muốn không giới hạn")
    private Integer maxAttempts;

    /** Cho thí sinh xem đáp án đúng + giải thích sau khi nộp. */
    private Boolean allowReview = true;

    /** Bài xếp trình độ đầu vào. Nên để công khai để học viên mới tìm thấy. */
    private Boolean isPlacement = false;

    /** Xáo thứ tự câu theo từng lượt (bài đọc vẫn liền khối, câu không đổi phần). */
    private Boolean shuffleQuestions = false;

    /** Xáo thứ tự đáp án theo từng lượt. */
    private Boolean shuffleOptions = false;
}
