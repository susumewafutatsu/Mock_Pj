package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClassUpdateRequest {

    @NotBlank(message = "Tên lớp không được để trống")
    @Size(max = 100, message = "Tên lớp tối đa 100 ký tự")
    private String className;

    @Size(max = 20, message = "Mã khóa học tối đa 20 ký tự")
    private String courseCode;

    @NotNull(message = "Trình độ không được để trống")
    private Integer levelId;
}
