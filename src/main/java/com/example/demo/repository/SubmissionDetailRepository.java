package com.example.demo.repository;

import com.example.demo.domain.model.SubmissionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// TODO: Add custom query methods:
// - findBySubmissionId(Long submissionId)
// - countBySubmissionIdAndIsCorrect(Long submissionId, boolean isCorrect)
@Repository
public interface SubmissionDetailRepository extends JpaRepository<SubmissionDetail, Long> {

}
