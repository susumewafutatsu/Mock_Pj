package com.example.demo.dto.request;

import com.example.demo.domain.enums.JoinPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Tạo phòng thi. Mã phòng do server sinh. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomCreateRequest {

    @NotBlank(message = "Tên phòng không được để trống")
    @Size(max = 100, message = "Tên phòng tối đa 100 ký tự")
    private String name;

    /** Đề thi của buổi thi. */
    private Integer examId;

    private Integer levelId;

    @Min(value = 1, message = "Sức chứa phải từ 1 người trở lên")
    private Integer capacity;

    private JoinPolicy joinPolicy;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Min(value = 0, message = "Số phút cho vào muộn không được âm")
    @Max(value = 180, message = "Số phút cho vào muộn tối đa 180")
    private Integer lateJoinMinutes;

    @Size(max = 2000, message = "Lời dặn tối đa 2000 ký tự")
    private String instructions;

    /** Mở sảnh chờ ngay sau khi tạo. */
    private Boolean openLobby;
}
