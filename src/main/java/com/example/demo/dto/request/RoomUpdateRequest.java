package com.example.demo.dto.request;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.RoomStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Sửa phòng. Trường null thì giữ nguyên. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomUpdateRequest {

    @Size(max = 100, message = "Tên phòng tối đa 100 ký tự")
    private String name;

    /** Đổi đề của buổi thi (chỉ trước khi bắt đầu). */
    private Integer examId;

    private Integer levelId;

    @Min(value = 1, message = "Sức chứa phải từ 1 người trở lên")
    private Integer capacity;

    private JoinPolicy joinPolicy;

    private RoomStatus status;

    /** Hẹn giờ bắt đầu làm bài. */
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** true = bỏ giờ hẹn. */
    private Boolean clearStartTime;

    @Min(value = 0, message = "Số phút cho vào muộn không được âm")
    @Max(value = 180, message = "Số phút cho vào muộn tối đa 180")
    private Integer lateJoinMinutes;

    /** Chuỗi rỗng = xoá lời dặn. */
    @Size(max = 2000, message = "Lời dặn tối đa 2000 ký tự")
    private String instructions;
}
