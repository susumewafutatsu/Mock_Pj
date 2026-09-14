package com.example.demo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Một lô đáp án client gom lại rồi mới gửi. */
@Data
public class SaveAnswersBatchRequest {

    /** Chặn trên để một request không thành một transaction khổng lồ. */
    public static final int MAX_ANSWERS = 200;

    /** Lượt làm mà lô này được soạn cho. */
    private Integer submissionId;

    @NotEmpty(message = "Lô đáp án rỗng")
    @Size(max = MAX_ANSWERS, message = "Một lô tối đa " + MAX_ANSWERS + " câu")
    @Valid
    private List<SaveAnswerRequest> answers = new ArrayList<>();
}
