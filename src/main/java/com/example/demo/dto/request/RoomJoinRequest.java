package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Vào phòng bằng mã. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomJoinRequest {

    @NotBlank(message = "Chưa nhập mã phòng")
    private String code;
}
