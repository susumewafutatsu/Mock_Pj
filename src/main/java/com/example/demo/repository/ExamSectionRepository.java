package com.example.demo.repository;

import com.example.demo.domain.model.ExamSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSectionRepository extends JpaRepository<ExamSection, Integer> {

    /** Các phần của một đề, theo đúng thứ tự thi. */
    List<ExamSection> findByExam_ExamIdOrderByOrderNoAsc(Integer examId);

    long countByExam_ExamId(Integer examId);

    void deleteByExam_ExamId(Integer examId);
}
