
package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnswerResponse {
    private Integer answerId;
    private String answerContent;
    private boolean correct;
}
