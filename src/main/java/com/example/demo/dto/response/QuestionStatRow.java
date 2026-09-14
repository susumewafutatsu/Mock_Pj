package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Một câu hỏi kèm tỉ lệ cả lớp làm đúng. */
@Data
@Builder
public class QuestionStatRow {

    private Integer questionId;
    private Integer questionOrder;

    /** Nội dung theo snapshot của đề — đúng thứ thí sinh đã nhìn thấy. */
    private String content;

    private List<String> tags;

    /** Số lượt trả lời (kể cả bỏ trắng, vì bài nộp vẫn ghi một dòng 0 điểm). */
    private long answered;
    private long correct;

    /** Tỉ lệ đúng 0–100. null khi chưa có ai làm câu này. */
    private Integer correctPercent;
}
