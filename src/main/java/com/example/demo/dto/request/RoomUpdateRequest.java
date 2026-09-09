package com.example.demo.dto.request;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.RoomStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Sửa phòng. Trường nào để null thì giữ nguyên giá trị cũ.
 *
 * Mã phòng KHÔNG sửa được: người ra đề đã đọc mã đó cho cả phòng, đổi giữa
 * chừng là cắt đường vào của những người còn đang gõ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomUpdateRequest {

    @Size(max = 100, message = "Tên phòng tối đa 100 ký tự")
    private String name;

    private Integer levelId;

    @Min(value = 1, message = "Sức chứa phải từ 1 người trở lên")
    private Integer capacity;

    private JoinPolicy joinPolicy;

    private RoomStatus status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
