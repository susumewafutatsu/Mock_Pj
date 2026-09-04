package com.example.demo.service.impl;

import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.ClassEntity;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.StudentClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentClassServiceImpl implements StudentClassService {

    private final ClassStudentRepository classStudentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ClassResponse> getMyClasses(String studentEmail) {
        User student = requireStudent(studentEmail);

        List<Integer> classIds = classStudentRepository.findClassIdsByStudentId(student.getUserId());
        if (classIds.isEmpty()) {
            return List.of();
        }

        // Ba câu truy vấn cố định, không phụ thuộc số lớp: lấy classId, nạp lớp
        // kèm giáo viên/trình độ/môn, đếm sĩ số cả lô.
        List<ClassEntity> classes = classRepository.findAllByIdWithDetails(classIds);
        Map<Integer, Long> headcounts = headcountsOf(classIds);

        List<ClassResponse> rows = new ArrayList<>(classes.size());
        for (ClassEntity cls : classes) {
            rows.add(toResponse(cls, headcounts.getOrDefault(cls.getClassId(), 0L)));
        }
        rows.sort(Comparator.comparing(ClassResponse::getClassName,
                Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    private Map<Integer, Long> headcountsOf(List<Integer> classIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (ClassStudentRepository.ClassHeadcount row
                : classStudentRepository.countByClassIdIn(classIds)) {
            counts.put(row.getClassId(), row.getTotal());
        }
        return counts;
    }

    private ClassResponse toResponse(ClassEntity cls, long studentCount) {
        SubjectLevel level = cls.getLevel();
        User teacher = cls.getTeacher();
        return ClassResponse.builder()
                .classId(cls.getClassId())
                .className(cls.getClassName())
                .courseCode(cls.getCourseCode())
                .teacherEmail(teacher == null ? null : teacher.getEmail())
                .teacherName(teacher == null ? null : teacher.getFullName())
                .levelId(level == null ? null : level.getLevelId())
                .levelName(level == null ? null : level.getLevelName())
                .subjectId(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectId())
                .subjectName(level == null || level.getSubject() == null
                        ? null : level.getSubject().getSubjectName())
                .studentCount(studentCount)
                .googleClassroomId(cls.getGoogleClassroomId())
                .createdAt(cls.getCreatedAt())
                .build();
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ học sinh mới xem được lớp học của mình");
        }
        return user;
    }
}
