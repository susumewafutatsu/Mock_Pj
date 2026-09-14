package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một lựa chọn của câu hỏi trắc nghiệm khi ôn lại trong sổ tay câu sai. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyOptionView {

    private Integer answerId;

    private String content;
}
