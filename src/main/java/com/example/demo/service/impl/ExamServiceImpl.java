package com.example.demo.service.impl;

import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.ClassEntity;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.ExamResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh sách đề của học sinh.
 *
 * Toàn bộ method chỉ đọc. Ba query cố định bất kể có bao nhiêu đề:
 * lớp của học sinh, các đề nhìn thấy được (đã join fetch lớp/môn/giáo viên),
 * và các phiên làm bài của học sinh; số câu mỗi đề lấy thêm một query gộp.
 * Không có vòng lặp nào đi xuống DB.
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ExamResponse> getExamsForStudent(String studentEmail) {
        User student = requireStudent(studentEmail);

        List<Integer> classIds = classStudentRepository.findClassIdsByStudentId(student.getUserId());
        List<Exam> exams = classIds.isEmpty()
                ? examRepository.findFreePracticeExams()
                : examRepository.findVisibleToStudent(classIds);
        if (exams.isEmpty()) {
            return List.of();
        }

        Map<Integer, Long> questionCounts = questionCountsOf(exams);
        Map<Integer, ExamSubmission> submissions = submissionsOf(student);

        LocalDateTime now = LocalDateTime.now();
        List<ExamResponse> rows = new ArrayList<>(exams.size());
        for (Exam exam : exams) {
            rows.add(toResponse(exam,
                    submissions.get(exam.getExamId()),
                    questionCounts.getOrDefault(exam.getExamId(), 0L).intValue(),
                    now));
        }
        return rows;
    }

    private ExamResponse toResponse(Exam exam, ExamSubmission submission, int totalQuestions,
                                    LocalDateTime now) {
        ClassEntity classEntity = exam.getClassEntity();
        SubjectLevel level = exam.getLevel();

        ExamResponse.Availability availability = resolveAvailability(exam, submission, totalQuestions, now);
        boolean inProgress = availability == ExamResponse.Availability.IN_PROGRESS;

        return ExamResponse.builder()
                .examId(exam.getExamId())
                .title(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .adaptive(Boolean.TRUE.equals(exam.getIsAdaptive()))
                .totalQuestions(totalQuestions)
                .className(classEntity == null ? null : classEntity.getClassName())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .levelName(level == null ? null : level.getLevelName())
                .teacherName(exam.getCreatedBy() == null ? null : exam.getCreatedBy().getFullName())
                .availability(availability)
                .submissionId(submission == null ? null : submission.getSubmissionId())
                .submissionStatus(submission == null ? null : submission.getStatus())
                // Chỉ phiên còn đang chạy mới có deadline đáng để client đếm ngược.
                .expiresAt(inProgress ? submission.getExpiresAt() : null)
                .remainingSeconds(inProgress ? submission.remainingSeconds(now) : 0L)
                .totalScore(submission == null || submission.isInProgress()
                        ? null : submission.getTotalScore())
                .submittedAt(submission == null ? null : submission.getSubmittedAt())
                .serverTime(now)
                .build();
    }

    /**
     * Trạng thái của đề với học sinh đang đăng nhập.
     *
     * Thứ tự xét quan trọng: đã nộp thì không quan tâm đề còn mở hay không nữa
     * (mỗi đề chỉ được làm một lần), và một phiên đang dở đã quá ExpiresAt vẫn
     * được coi là SUBMITTED — bài đó chắc chắn sẽ bị nộp tự động ở request kế
     * tiếp hoặc bởi job quét, nên không nên hiện nút "Tiếp tục" cho nó.
     */
    private ExamResponse.Availability resolveAvailability(Exam exam, ExamSubmission submission,
                                                          int totalQuestions, LocalDateTime now) {
        if (submission != null) {
            if (!submission.isInProgress()) {
                return ExamResponse.Availability.SUBMITTED;
            }
            return submission.isExpiredAt(now)
                    ? ExamResponse.Availability.SUBMITTED
                    : ExamResponse.Availability.IN_PROGRESS;
        }
        if (totalQuestions == 0) {
            return ExamResponse.Availability.NO_QUESTIONS;
        }
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            return ExamResponse.Availability.UPCOMING;
        }
        if (exam.getEndTime() != null && !now.isBefore(exam.getEndTime())) {
            return ExamResponse.Availability.CLOSED;
        }
        return ExamResponse.Availability.OPEN;
    }

    private Map<Integer, Long> questionCountsOf(List<Exam> exams) {
        List<Integer> examIds = exams.stream().map(Exam::getExamId).toList();
        Map<Integer, Long> counts = new HashMap<>();
        for (ExamQuestionRepository.ExamQuestionCount row
                : examQuestionRepository.countByExamIdIn(examIds)) {
            counts.put(row.getExamId(), row.getTotal());
        }
        return counts;
    }

    /** Phiên làm bài của học sinh, tra theo ExamID. Tối đa một phiên mỗi đề. */
    private Map<Integer, ExamSubmission> submissionsOf(User student) {
        Map<Integer, ExamSubmission> map = new HashMap<>();
        for (ExamSubmission submission : submissionRepository.findByStudentUserId(student.getUserId())) {
            map.put(submission.getExam().getExamId(), submission);
        }
        return map;
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ học sinh mới xem được danh sách đề thi của mình");
        }
        return user;
    }
}
