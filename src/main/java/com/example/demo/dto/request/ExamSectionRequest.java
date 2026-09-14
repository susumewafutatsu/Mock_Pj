package com.example.demo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Một phần thi khi người ra đề tự khai cấu trúc đề. */
@Data
public class ExamSectionRequest {

    /** Có id = sửa phần đang có; để trống = phần mới. */
    private Integer sectionId;

    @NotBlank(message = "Tên phần thi không được để trống")
    private String name;

    @NotNull(message = "Thời lượng phần thi không được để trống")
    @Min(value = 1, message = "Phần thi tối thiểu 1 phút")
    @Max(value = 300, message = "Phần thi tối đa 300 phút")
    private Integer durationMinutes;

    /** Thứ tự thi. Để trống thì lấy theo vị trí trong danh sách gửi lên. */
    private Integer orderNo;
}
