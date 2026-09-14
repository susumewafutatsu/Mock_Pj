package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Tạo / sửa bộ thẻ của người học. */
@Data
public class DeckRequest {

    @NotBlank(message = "Chưa đặt tên bộ thẻ")
    @Size(max = 100, message = "Tên bộ thẻ tối đa 100 ký tự")
    private String name;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    private Integer levelId;
}
