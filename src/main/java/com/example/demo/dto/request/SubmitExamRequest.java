package com.example.demo.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Nộp bài. */
@Data
public class SubmitExamRequest {

    @Valid
    private List<SaveAnswerRequest> answers = new ArrayList<>();
}
