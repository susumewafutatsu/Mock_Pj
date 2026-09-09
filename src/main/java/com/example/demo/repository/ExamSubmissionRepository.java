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

    /** Số phiên làm bài của một đề — dùng cho cột "Nộp bài" ở màn người ra đề. */
    long countByExamExamId(Integer examId);

    /**
     * Phiên còn đang làm dở của một thí sinh trên một đề.
     *
     * Từ v1.2.0 một thí sinh có thể có nhiều phiên trên cùng một đề (nhiều lượt
     * làm), nhưng nhiều nhất MỘT phiên đang dở tại một thời điểm: lượt mới chỉ
     * được tạo sau khi lượt trước đã chốt. Đây là điểm vào của mọi thao tác
     * trong phòng thi — autosave, heartbeat, nộp bài.
     */
    Optional<ExamSubmission> findByExam_ExamIdAndStudent_UserIdAndStatus(
            Integer examId, String studentId, SubmissionStatus status);

    /**
     * Lượt gần nhất của một thí sinh trên một đề, kể cả đã nộp.
     * Dùng cho danh sách đề: hiện điểm của lần làm gần nhất.
     */
    Optional<ExamSubmission> findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(
            Integer examId, String studentId);

    /** Số lượt thí sinh đã dùng trên một đề — vế trái của phép so với MaxAttempts. */
    long countByExam_ExamIdAndStudent_UserId(Integer examId, String studentId);

    /** Các phiên đã quá giờ mà vẫn chưa nộp — đầu vào của job tự động nộp bài. */
    List<ExamSubmission> findByStatusAndExpiresAtLessThanEqual(
            SubmissionStatus status, LocalDateTime deadline);

    /** Các phiên đang làm mà im lặng quá lâu — nghi rớt mạng, bật AtRiskStatus. */
    List<ExamSubmission> findByStatusAndAtRiskStatusFalseAndLastActiveAtLessThan(
            SubmissionStatus status, LocalDateTime threshold);
}
