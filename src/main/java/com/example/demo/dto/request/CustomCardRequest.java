package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Thẻ tự soạn. */
@Data
public class CustomCardRequest {

    @NotBlank(message = "Chưa nhập mặt trước")
    @Size(max = 200, message = "Mặt trước tối đa 200 ký tự")
    private String front;

    @Size(max = 200, message = "Cách đọc tối đa 200 ký tự")
    private String reading;

    @NotBlank(message = "Chưa nhập mặt sau")
    @Size(max = 500, message = "Mặt sau tối đa 500 ký tự")
    private String back;

    @Size(max = 500, message = "Câu ví dụ tối đa 500 ký tự")
    private String example;

    @Size(max = 500, message = "Nghĩa câu ví dụ tối đa 500 ký tự")
    private String exampleMeaning;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
