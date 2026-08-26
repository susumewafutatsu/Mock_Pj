package com.example.demo.repository;

import com.example.demo.domain.model.SubmissionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionDetailRepository extends JpaRepository<SubmissionDetail, Integer> {

    List<SubmissionDetail> findBySubmission_SubmissionId(Integer submissionId);

    long countBySubmission_SubmissionIdAndIsCorrect(Integer submissionId, boolean isCorrect);

    /** Câu hỏi này đã có học sinh trả lời chưa. */
    boolean existsByQuestion_QuestionId(Integer questionId);
}
