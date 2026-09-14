package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một đoạn văn đọc hiểu trong ngân hàng câu hỏi. */
@Data
@Builder
public class ReadingPassageResponse {

    private Integer passageId;
    private Integer bankId;
    private String title;
    private String content;
    private LocalDateTime createdAt;

    /** Số câu hỏi đang dùng đoạn này. */
    private long questionCount;
}
