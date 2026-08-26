package com.example.demo.repository;

import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamQuestionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, ExamQuestionKey> {

    List<ExamQuestion> findByExam_ExamIdOrderByQuestionOrderAsc(Integer examId);

    Optional<ExamQuestion> findByExam_ExamIdAndQuestion_QuestionId(Integer examId, Integer questionId);

    boolean existsByQuestion_QuestionId(Integer questionId);

    long countByExam_ExamId(Integer examId);
}
