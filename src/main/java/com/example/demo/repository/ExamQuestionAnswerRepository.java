package com.example.demo.repository;

import com.example.demo.domain.model.ExamQuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamQuestionAnswerRepository extends JpaRepository<ExamQuestionAnswer, Integer> {

    /** Đáp án hiển thị cho học sinh. Không trả về cờ IsCorrect ra ngoài API. */
    List<ExamQuestionAnswer> findByExamQuestion_Exam_ExamIdAndExamQuestion_Question_QuestionIdOrderByAnswerOrderAsc(
            Integer examId, Integer questionId);

    /** Dùng khi chấm: đáp án học sinh chọn có thuộc đúng câu hỏi của đề này không. */
    Optional<ExamQuestionAnswer> findBySnapshotAnswerIdAndExamQuestion_Exam_ExamId(
            Integer snapshotAnswerId, Integer examId);

    void deleteByExamQuestion_Exam_ExamIdAndExamQuestion_Question_QuestionId(
            Integer examId, Integer questionId);
}
