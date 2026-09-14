package com.example.demo.service.cache;

import com.example.demo.dto.response.ExamQuestionView;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Vỏ bọc quanh danh sách câu hỏi khi cất vào Redis. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedExamPaper {

    private List<ExamQuestionView> questions;
}
