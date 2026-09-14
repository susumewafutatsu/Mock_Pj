package com.example.demo.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** Nhân bản phòng cho buổi thi sau. */
@Data
public class RoomDuplicateRequest {

    /** Để trống = "<tên cũ> (buổi mới)". */
    @Size(max = 100, message = "Tên phòng tối đa 100 ký tự")
    private String name;

    /** Giữ nguyên danh sách thí sinh đang ở phòng cũ. */
    private Boolean keepMembers;

    private LocalDateTime startTime;
}
