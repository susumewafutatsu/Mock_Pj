package com.example.demo.service.impl;

import com.example.demo.domain.model.ClassEntity;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.ExamCreateRequest;
import com.example.demo.dto.response.TeacherExamResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
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
    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
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
                .classEntity(resolveOwnedClassOrNull(teacher, request.getClassId()))
                .level(resolveLevel(request.getLevelId()))
                .createdBy(teacher)
                .durationMinutes(request.getDurationMinutes())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isAdaptive(Boolean.TRUE.equals(request.getAdaptive()))
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
            throw new BusinessException("Đề đã có học sinh làm bài, không thể sửa");
        }

        exam.setTitle(request.getTitle().trim());
        exam.setClassEntity(resolveOwnedClassOrNull(teacher, request.getClassId()));
        exam.setLevel(resolveLevel(request.getLevelId()));
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setIsAdaptive(Boolean.TRUE.equals(request.getAdaptive()));

        // Sửa đề là bản cache trong Redis hết đúng. Xoá sau commit để không có
        // request nào kịp nạp lại cache từ dữ liệu cũ chưa commit.
        evictPaperCacheAfterCommit(examId);
        return toResponse(examRepository.save(exam), LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(String teacherEmail, Integer examId) {
        User teacher = requireTeacher(teacherEmail);
        Exam exam = requireOwnedExam(teacher, examId);

        if (submissionRepository.existsByExamExamId(examId)) {
            throw new BusinessException("Đề đã có học sinh làm bài, không thể xóa");
        }

        // ExamQuestions (và ExamQuestionAnswers theo sau) có FK ON DELETE CASCADE
        // nên xoá đề là xoá luôn snapshot câu hỏi của đề.
        examRepository.delete(exam);
        evictPaperCacheAfterCommit(examId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Xoá bản cache đề thi trong Redis, nhưng chỉ sau khi transaction commit —
     * xoá sớm thì một request đọc song song có thể nạp lại cache bằng dữ liệu cũ
     * và không còn ai đi xoá lần nữa.
     */
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

    /** classId để trống = đề luyện tập tự do. Có thì phải là lớp của chính giáo viên. */
    private ClassEntity resolveOwnedClassOrNull(User teacher, Integer classId) {
        if (classId == null) {
            return null;
        }
        ClassEntity cls = classRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
        if (!cls.getTeacher().getUserId().equals(teacher.getUserId())) {
            throw new BusinessException("Bạn không phụ trách lớp học này");
        }
        return cls;
    }

    private SubjectLevel resolveLevel(Integer levelId) {
        return subjectLevelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trình độ id=" + levelId));
    }

    private void requireValidWindow(ExamCreateRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("Thời gian đóng đề phải sau thời gian mở đề");
        }
    }

    private TeacherExamResponse toResponse(Exam exam, LocalDateTime now) {
        int totalQuestions = (int) examQuestionRepository.countByExam_ExamId(exam.getExamId());
        ClassEntity cls = exam.getClassEntity();
        SubjectLevel level = exam.getLevel();

        return TeacherExamResponse.builder()
                .examId(exam.getExamId())
                .title(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .adaptive(Boolean.TRUE.equals(exam.getIsAdaptive()))
                .classId(cls != null ? cls.getClassId() : null)
                .className(cls != null ? cls.getClassName() : null)
                .levelId(level != null ? level.getLevelId() : null)
                .levelName(level != null ? level.getLevelName() : null)
                .subjectName(level != null && level.getSubject() != null
                        ? level.getSubject().getSubjectName() : null)
                .totalQuestions(totalQuestions)
                .submissionCount(submissionRepository.countByExamExamId(exam.getExamId()))
                .totalStudents(cls != null
                        ? classStudentRepository.countByClassEntity_ClassId(cls.getClassId()) : 0)
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
