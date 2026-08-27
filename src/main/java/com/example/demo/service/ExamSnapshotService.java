package com.example.demo.service;

import com.example.demo.dto.request.ExamQuestionSelection;

import java.util.List;

public interface ExamSnapshotService {

    int attachQuestions(Integer examId, List<ExamQuestionSelection> selections, String teacherEmail);

    void refreshSnapshot(Integer examId, Integer questionId, String teacherEmail);

    void detachQuestion(Integer examId, Integer questionId, String teacherEmail);
}
