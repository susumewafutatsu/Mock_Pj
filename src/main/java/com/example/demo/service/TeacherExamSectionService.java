package com.example.demo.service;

import com.example.demo.dto.request.ExamSectionRequest;
import com.example.demo.dto.response.ExamSectionView;

import java.util.List;

/** Phần thi của một đề, phía người ra đề. */
public interface TeacherExamSectionService {

    List<ExamSectionView> list(Integer examId, String teacherEmail);

    /** Thay toàn bộ danh sách phần của đề. */
    List<ExamSectionView> replaceAll(Integer examId, List<ExamSectionRequest> sections,
                                     String teacherEmail);

    /** Sinh các phần theo đúng cấu trúc JLPT của trình độ gắn với đề, và bật chấm theo thang quy đổi. */
    List<ExamSectionView> applyJlptTemplate(Integer examId, String teacherEmail);

    void deleteAll(Integer examId, String teacherEmail);
}
