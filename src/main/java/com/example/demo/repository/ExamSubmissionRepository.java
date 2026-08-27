package com.example.demo.repository;

import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Integer> {
    List<ExamSubmission> findByStudentUserId(String studentId);
    List<ExamSubmission> findByExamExamId(Integer examId);

    /** Đề đã có người bắt đầu làm chưa — mốc để khoá snapshot. */
    boolean existsByExamExamId(Integer examId);

    /**
     * Phiên thi duy nhất của một học sinh trên một đề.
     * Có UNIQUE(ExamID, StudentID) bảo đảm nhiều nhất một dòng.
     */
    Optional<ExamSubmission> findByExam_ExamIdAndStudent_UserId(Integer examId, String studentId);

    /** Các phiên đã quá giờ mà vẫn chưa nộp — đầu vào của job tự động nộp bài. */
    List<ExamSubmission> findByStatusAndExpiresAtLessThanEqual(
            SubmissionStatus status, LocalDateTime deadline);

    /** Các phiên đang làm mà im lặng quá lâu — nghi rớt mạng, bật AtRiskStatus. */
    List<ExamSubmission> findByStatusAndAtRiskStatusFalseAndLastActiveAtLessThan(
            SubmissionStatus status, LocalDateTime threshold);
}
