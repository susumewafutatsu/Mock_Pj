package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tạo / sửa khoá học.
 *
 * Không có trường trạng thái: khoá mới luôn ở DRAFT, và việc chuyển trạng thái
 * đi qua các endpoint riêng (submit / approve / reject) chứ không phải bằng cách
 * client gửi lên một chữ. Để client tự đặt status là mở đường cho người ra đề
 * tự xuất bản khoá của mình.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseCreateRequest {

    @NotBlank(message = "Tên khoá học không được để trống")
    @Size(max = 200, message = "Tên khoá học tối đa 200 ký tự")
    private String title;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    private Integer levelId;
}
