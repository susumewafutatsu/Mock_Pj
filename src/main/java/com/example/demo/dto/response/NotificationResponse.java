package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một thông báo trong ô chuông. */
@Data
@Builder
public class NotificationResponse {

    private Integer notificationId;
    private String kind;
    private String subject;
    private String message;

    /** Đường dẫn trong app; null = thông báo chỉ để đọc. */
    private String link;

    // @JsonProperty: không có thì Jackson lấy tên theo getter isRead() → "read" cũng được.
    @JsonProperty("read")
    private boolean read;

    private LocalDateTime createdAt;
}
