package com.example.demo.repository;

import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Integer> {
    List<ExamSubmission> findByStudentUserId(String studentId);
    List<ExamSubmission> findByExamExamId(Integer examId);

    /** Đề đã có người bắt đầu làm chưa — mốc để khoá snapshot. */
    boolean existsByExamExamId(Integer examId);

    /** Số phiên làm bài của một đề — dùng cho cột "Nộp bài" ở màn người ra đề. */
    long countByExamExamId(Integer examId);

    /** Phiên còn đang làm dở của một thí sinh trên một đề. */
    Optional<ExamSubmission> findByExam_ExamIdAndStudent_UserIdAndStatus(
            Integer examId, String studentId, SubmissionStatus status);

    /** Lượt gần nhất của một thí sinh trên một đề, kể cả đã nộp. */
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

    // ── Trang quản trị ──────────────────────────────────────────────────────

    long countByStatus(SubmissionStatus status);

    /** Đang làm và nghi rớt mạng — cùng cờ người ra đề thấy đèn đỏ. */
    long countByStatusAndAtRiskStatusTrue(SubmissionStatus status);

    long countBySubmittedAtGreaterThanEqual(LocalDateTime since);

    /** Thí sinh có đang dở một bài nào không — chặn đổi vai trò giữa lúc thi. */
    boolean existsByStudent_UserIdAndStatus(String studentId, SubmissionStatus status);

    // ── Phòng thi & bảng xếp hạng ───────────────────────────────────────────

    /** Người ra đề kết thúc phòng sớm. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ExamSubmission s set s.expiresAt = :now
            where s.status = com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
              and s.expiresAt > :now
              and s.exam.examId in (select re.id.examId from RoomExam re where re.id.roomId = :roomId)
              and s.student.userId in (select m.id.userId from RoomMember m where m.id.roomId = :roomId)
            """)
    int cutRunningSessionsOfRoom(@Param("roomId") Integer roomId, @Param("now") LocalDateTime now);

    /** Mọi lượt của một nhóm thí sinh trên một đề — nguyên liệu bảng xếp hạng phòng. */
    List<ExamSubmission> findByExam_ExamIdAndStudent_UserIdIn(Integer examId, Collection<String> studentIds);

    /** Mọi lượt đã nộp của một đề, kèm thí sinh — bảng xếp hạng đề tự do. */
    @Query("""
            select s from ExamSubmission s
            join fetch s.student
            where s.exam.examId = :examId
              and s.status <> com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
            """)
    List<ExamSubmission> findFinishedWithStudentByExamId(@Param("examId") Integer examId);
}
