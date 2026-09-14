package com.example.demo.service.impl;


import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.ExamCreateRequest;
import com.example.demo.dto.response.TeacherExamResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.RoomExamRepository;
import com.example.demo.repository.RoomMemberRepository;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.SubjectLevelRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TeacherExamService;
import com.example.demo.service.cache.ExamRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherExamServiceImpl implements TeacherExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final RoomExamRepository roomExamRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SubjectLevelRepository subjectLevelRepository;
    private final UserRepository userRepository;
    private final ExamRedisService examRedis;

    @Override
    @Transactional(readOnly = true)
    public List<TeacherExamResponse> listMine(String teacherEmail) {
        User teacher = requireTeacher(teacherEmail);
        LocalDateTime now = LocalDateTime.now();
        return examRepository.findByCreatedByUserId(teacher.getUserId())
                .stream()
                .sorted(Comparator.comparing(Exam::getExamId).reversed())
                .map(exam -> toResponse(exam, now))
                .toList();
    }

    @Override
    @Transactional
    public TeacherExamResponse create(String teacherEmail, ExamCreateRequest request) {
        User teacher = requireTeacher(teacherEmail);
        requireValidWindow(request);

        Exam exam = Exam.builder()
                .title(request.getTitle().trim())
                .isPublic(Boolean.TRUE.equals(request.getIsPublic()))
                .level(resolveLevel(request.getLevelId()))
                .createdBy(teacher)
                .durationMinutes(request.getDurationMinutes())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isAdaptive(Boolean.TRUE.equals(request.getAdaptive()))
                .maxAttempts(request.getMaxAttempts())
                // Không dùng Boolean.TRUE.equals: bỏ trống trường này phải giữ
                // mặc định "cho xem", chứ không thành "cấm xem".
                .allowReview(request.getAllowReview() == null || request.getAllowReview())
                .isPlacement(Boolean.TRUE.equals(request.getIsPlacement()))
                .shuffleQuestions(Boolean.TRUE.equals(request.getShuffleQuestions()))
                .shuffleOptions(Boolean.TRUE.equals(request.getShuffleOptions()))
                .build();

        return toResponse(examRepository.save(exam), LocalDateTime.now());
    }

    @Override
    @Transactional
    public TeacherExamResponse update(String teacherEmail, Integer examId, ExamCreateRequest request) {
        User teacher = requireTeacher(teacherEmail);
        Exam exam = requireOwnedExam(teacher, examId);
        requireValidWindow(request);

        if (submissionRepository.existsByExamExamId(examId)) {
            throw new BusinessException("Đề đã có thí sinh làm bài, không thể sửa");
        }

        exam.setTitle(request.getTitle().trim());
        exam.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()));
        exam.setLevel(resolveLevel(request.getLevelId()));
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setIsAdaptive(Boolean.TRUE.equals(request.getAdaptive()));
        exam.setMaxAttempts(request.getMaxAttempts());
        exam.setAllowReview(request.getAllowReview() == null || request.getAllowReview());
        exam.setIsPlacement(Boolean.TRUE.equals(request.getIsPlacement()));
        exam.setShuffleQuestions(Boolean.TRUE.equals(request.getShuffleQuestions()));
        exam.setShuffleOptions(Boolean.TRUE.equals(request.getShuffleOptions()));

        // Sửa đề là bản cache trong Redis hết đúng.
        evictPaperCacheAfterCommit(examId);
        return toResponse(examRepository.save(exam), LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(String teacherEmail, Integer examId) {
        User teacher = requireTeacher(teacherEmail);
        Exam exam = requireOwnedExam(teacher, examId);

        if (submissionRepository.existsByExamExamId(examId)) {
            throw new BusinessException("Đề đã có thí sinh làm bài, không thể xóa");
        }

        // ExamQuestions (và ExamQuestionAnswers theo sau) có FK ON DELETE CASCADE
        // nên xoá đề là xoá luôn snapshot câu hỏi của đề.
        examRepository.delete(exam);
        evictPaperCacheAfterCommit(examId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Xoá bản cache đề thi trong Redis, nhưng chỉ sau khi transaction commit. */
    private void evictPaperCacheAfterCommit(Integer examId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            examRedis.evictPaper(examId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                examRedis.evictPaper(examId);
            }
        });
    }

    private User requireTeacher(String teacherEmail) {
        return userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + teacherEmail));
    }

    private Exam requireOwnedExam(User teacher, Integer examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        if (!exam.getCreatedBy().getUserId().equals(teacher.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId);
        }
        return exam;
    }

    private SubjectLevel resolveLevel(Integer levelId) {
        return subjectLevelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trình độ id=" + levelId));
    }

    /** Khung giờ mở đề giờ để trống được (xem {@link ExamCreateRequest#getStartTime()}) */
    private void requireValidWindow(ExamCreateRequest request) {
        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException("Thời gian đóng đề phải sau thời gian mở đề");
        }
    }

    private TeacherExamResponse toResponse(Exam exam, LocalDateTime now) {
        int totalQuestions = (int) examQuestionRepository.countByExam_ExamId(exam.getExamId());
        // Một đề gắn được vào nhiều phòng, nên ở đây phải đếm chứ không đọc
        // được một cột như thời còn ClassID.
        List<Integer> roomIds = roomExamRepository.findRoomIdsByExamId(exam.getExamId());
        SubjectLevel level = exam.getLevel();

        return TeacherExamResponse.builder()
                .examId(exam.getExamId())
                .title(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .adaptive(Boolean.TRUE.equals(exam.getIsAdaptive()))
                .maxAttempts(exam.getMaxAttempts())
                .allowReview(Boolean.TRUE.equals(exam.getAllowReview()))
                .isPublic(Boolean.TRUE.equals(exam.getIsPublic()))
                .isPlacement(Boolean.TRUE.equals(exam.getIsPlacement()))
                .shuffleQuestions(Boolean.TRUE.equals(exam.getShuffleQuestions()))
                .shuffleOptions(Boolean.TRUE.equals(exam.getShuffleOptions()))
                .jlptScoring(Boolean.TRUE.equals(exam.getJlptScoring()))
                .roomCount(roomIds.size())
                .levelId(level != null ? level.getLevelId() : null)
                .levelName(level != null ? level.getLevelName() : null)
                .subjectName(level != null && level.getSubject() != null
                        ? level.getSubject().getSubjectName() : null)
                .totalQuestions(totalQuestions)
                .submissionCount(submissionRepository.countByExamExamId(exam.getExamId()))
                // Cộng sĩ số của mọi phòng có chứa đề này.
                .totalCandidates(roomIds.isEmpty() ? 0L
                        : roomMemberRepository.countActiveByRoomIdIn(roomIds).stream()
                                .mapToLong(RoomMemberRepository.RoomHeadcount::getTotal).sum())
                .status(resolveStatus(exam, totalQuestions, now))
                .createdAt(exam.getCreatedAt())
                .serverTime(now)
                .build();
    }

    private TeacherExamResponse.Status resolveStatus(Exam exam, int totalQuestions, LocalDateTime now) {
        if (totalQuestions == 0) {
            return TeacherExamResponse.Status.NO_QUESTIONS;
        }
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            return TeacherExamResponse.Status.UPCOMING;
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            return TeacherExamResponse.Status.CLOSED;
        }
        return TeacherExamResponse.Status.OPEN;
    }
}
