package com.example.demo.dto.request;

import com.example.demo.domain.enums.JoinPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Tạo phòng thi mới. Mã phòng do server sinh, client không được tự đặt. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomCreateRequest {

    @NotBlank(message = "Tên phòng không được để trống")
    @Size(max = 100, message = "Tên phòng tối đa 100 ký tự")
    private String name;

    private Integer levelId;

    /**
     * Sức chứa. Bỏ trống = không giới hạn.
     *
     * Không cho 0: một phòng không nhận được ai thì tạo ra để làm gì. Muốn
     * chặn người vào thì để phòng ở trạng thái DRAFT.
     */
    @Min(value = 1, message = "Sức chứa phải từ 1 người trở lên")
    private Integer capacity;

    private JoinPolicy joinPolicy;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
