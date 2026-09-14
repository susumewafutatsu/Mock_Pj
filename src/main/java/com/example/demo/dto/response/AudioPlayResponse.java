package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

/** Kết quả xin phát file nghe: server đã đếm lượt này. */
@Data
@Builder
public class AudioPlayResponse {
    private Integer questionId;
    private String audioUrl;
    /** Số lượt đã nghe, tính cả lượt vừa xin. */
    private int plays;
    private int maxPlays;
    private int remaining;
}
