package com.example.demo.service.impl;

import com.example.demo.domain.enums.JlptLevel;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamSection;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.ExamSectionRequest;
import com.example.demo.dto.response.ExamSectionView;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSectionRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TeacherExamSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Xem {@link TeacherExamSectionService}. */
@Service
@RequiredArgsConstructor
public class TeacherExamSectionServiceImpl implements TeacherExamSectionService {

    /** Tên và thời lượng các khối thời gian, theo từng cấp. */
    private static final Map<JlptLevel, List<Object[]>> TEMPLATES = new LinkedHashMap<>();

    static {
        TEMPLATES.put(JlptLevel.N1, List.of(
                new Object[]{"言語知識（文字・語彙・文法）・読解", 110},
                new Object[]{"聴解", 55}));
        TEMPLATES.put(JlptLevel.N2, List.of(
                new Object[]{"言語知識（文字・語彙・文法）・読解", 105},
                new Object[]{"聴解", 50}));
        TEMPLATES.put(JlptLevel.N3, List.of(
                new Object[]{"言語知識（文字・語彙）", 30},
                new Object[]{"言語知識（文法）・読解", 70},
                new Object[]{"聴解", 40}));
        TEMPLATES.put(JlptLevel.N4, List.of(
                new Object[]{"言語知識（文字・語彙）", 25},
                new Object[]{"言語知識（文法）・読解", 55},
                new Object[]{"聴解", 35}));
        TEMPLATES.put(JlptLevel.N5, List.of(
                new Object[]{"言語知識（文字・語彙）", 20},
                new Object[]{"言語知識（文法）・読解", 40},
                new Object[]{"聴解", 30}));
    }

    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository submissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ExamSectionView> list(Integer examId, String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        return toViews(sectionRepository.findByExam_ExamIdOrderByOrderNoAsc(exam.getExamId()));
    }

    @Override
    @Transactional
    public List<ExamSectionView> replaceAll(Integer examId, List<ExamSectionRequest> sections,
                                            String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        requireNotStarted(examId);

        if (sections == null || sections.isEmpty()) {
            deleteAllSections(exam);
            return List.of();
        }

        // Xoá rồi tạo lại thay vì so khớp từng phần.
        deleteAllSections(exam);

        List<ExamSection> saved = new ArrayList<>();
        int order = 1;
        int totalMinutes = 0;
        for (ExamSectionRequest request : sections) {
            ExamSection section = ExamSection.builder()
                    .exam(exam)
                    .name(request.getName().trim())
                    .durationMinutes(request.getDurationMinutes())
                    .orderNo(request.getOrderNo() != null ? request.getOrderNo() : order)
                    .build();
            saved.add(sectionRepository.save(section));
            totalMinutes += request.getDurationMinutes();
            order++;
        }

        // Thời lượng đề = tổng các phần.
        exam.setDurationMinutes(totalMinutes);
        examRepository.save(exam);

        return toViews(saved);
    }

    @Override
    @Transactional
    public List<ExamSectionView> applyJlptTemplate(Integer examId, String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        requireNotStarted(examId);

        JlptLevel level = exam.getLevel() == null ? null
                : JlptLevel.fromLevelName(exam.getLevel().getLevelName());
        if (level == null) {
            throw new BusinessException(
                    "Đề này không gắn với một cấp JLPT (N5–N1) nên không có cấu trúc chuẩn để áp. "
                    + "Hãy chọn trình độ JLPT cho đề, hoặc tự khai các phần thi.");
        }

        List<ExamSectionRequest> requests = new ArrayList<>();
        int order = 1;
        for (Object[] template : TEMPLATES.get(level)) {
            ExamSectionRequest request = new ExamSectionRequest();
            request.setName((String) template[0]);
            request.setDurationMinutes((Integer) template[1]);
            request.setOrderNo(order++);
            requests.add(request);
        }

        List<ExamSectionView> views = replaceAll(examId, requests, teacherEmail);

        // Áp khuôn JLPT thì cũng chấm theo thang JLPT.
        exam.setJlptScoring(true);
        examRepository.save(exam);

        return views;
    }

    @Override
    @Transactional
    public void deleteAll(Integer examId, String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        requireNotStarted(examId);
        deleteAllSections(exam);
        // Không còn phần thì cũng không còn cấu trúc JLPT để chấm theo.
        exam.setJlptScoring(false);
        examRepository.save(exam);
    }

    // ── Phần dùng chung ─────────────────────────────────────────────────────

    private void deleteAllSections(Exam exam) {
        List<ExamSection> existing =
                sectionRepository.findByExam_ExamIdOrderByOrderNoAsc(exam.getExamId());
        if (existing.isEmpty()) {
            return;
        }
        // Gỡ câu ra khỏi phần TRƯỚC khi xoá phần.
        for (ExamQuestion question : examQuestionRepository
                .findByExam_ExamIdOrderByQuestionOrderAsc(exam.getExamId())) {
            if (question.getSection() != null) {
                question.setSection(null);
                examQuestionRepository.save(question);
            }
        }
        sectionRepository.deleteAll(existing);
    }

    private List<ExamSectionView> toViews(List<ExamSection> sections) {
        List<ExamSectionView> views = new ArrayList<>();
        for (ExamSection section : sections) {
            long inSection = examQuestionRepository
                    .findByExam_ExamIdOrderByQuestionOrderAsc(section.getExam().getExamId()).stream()
                    .filter(q -> q.getSection() != null
                            && q.getSection().getSectionId().equals(section.getSectionId()))
                    .count();
            views.add(ExamSectionView.builder()
                    .sectionId(section.getSectionId())
                    .name(section.getName())
                    .orderNo(section.getOrderNo())
                    .durationMinutes(section.getDurationMinutes())
                    .totalQuestions((int) inSection)
                    .build());
        }
        return views;
    }

    private void requireNotStarted(Integer examId) {
        if (submissionRepository.existsByExamExamId(examId)) {
            throw new BusinessException(
                    "Đề đã có thí sinh làm bài, không đổi được cấu trúc phần thi nữa.");
        }
    }

    private Exam requireOwnedExam(Integer examId, String teacherEmail) {
        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + teacherEmail));
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));
        if (!exam.getCreatedBy().getUserId().equals(teacher.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId);
        }
        return exam;
    }
}
