package com.example.demo.repository;

import com.example.demo.domain.model.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Integer> {
    List<ExamSubmission> findByStudentUserId(String studentId);
    List<ExamSubmission> findByExamExamId(Integer examId);

    /** Đề đã có người bắt đầu làm chưa — mốc để khoá snapshot. */
    boolean existsByExamExamId(Integer examId);
}