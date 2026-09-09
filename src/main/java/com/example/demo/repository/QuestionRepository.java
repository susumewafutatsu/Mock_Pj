package com.example.demo.repository;

import com.example.demo.domain.model.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository
        extends JpaRepository<Question, Integer>, JpaSpecificationExecutor<Question> {
public interface QuestionRepository extends JpaRepository<Question, Integer> {

    Optional<Question> findByQuestionIdAndBank_BankIdAndIsDeletedFalse(Integer questionId, Integer bankId);

    Page<Question> findByBank_BankIdAndIsDeletedFalse(Integer bankId, Pageable pageable);

    List<Question> findByBank_BankIdAndIsDeletedFalseAndDifficultyLevelBetween(
            Integer bankId, Integer minDifficulty, Integer maxDifficulty);

    /** Số câu hỏi còn hiệu lực trong một ngân hàng (bỏ qua câu đã xoá mềm). */
    long countByBank_BankIdAndIsDeletedFalse(Integer bankId);

    /** Câu hỏi đã được đưa vào ít nhất một đề thi. */
    @Query("""
            SELECT COUNT(eq) > 0 FROM ExamQuestion eq
            WHERE eq.id.questionId = :questionId
            """)
    boolean isUsedInAnyExam(@Param("questionId") Integer questionId);
}
