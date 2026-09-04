package com.example.demo.service.impl;

import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.ClassEntity;
import com.example.demo.domain.model.ClassStudent;
import com.example.demo.domain.model.ClassStudentKey;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.ClassCreateRequest;
import com.example.demo.dto.request.ClassUpdateRequest;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.dto.response.ClassStudentResponse;
import com.example.demo.repository.ClassRepository;
import com.example.demo.repository.ClassStudentRepository;
import com.example.demo.repository.SubjectLevelRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassRepository classRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final SubjectLevelRepository subjectLevelRepository;

    private User resolveTeacher(String teacherEmail) {
        return userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giáo viên: " + teacherEmail));
    }

    private ClassEntity resolveOwnedClass(String teacherEmail, Integer classId) {
        ClassEntity cls = classRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp học ID: " + classId));
        if (!cls.getTeacher().getEmail().equals(teacherEmail)) {
            throw new SecurityException("Bạn không có quyền thao tác với lớp học này");
        }
        return cls;
    }

    private ClassResponse toResponse(ClassEntity cls) {
        long studentCount = classStudentRepository.countByClassEntity_ClassId(cls.getClassId());
        SubjectLevel level = cls.getLevel();
        return ClassResponse.builder()
                .classId(cls.getClassId())
                .className(cls.getClassName())
                .courseCode(cls.getCourseCode())
                .teacherEmail(cls.getTeacher().getEmail())
                .teacherName(cls.getTeacher().getFullName())
                .levelId(level != null ? level.getLevelId() : null)
                .levelName(level != null ? level.getLevelName() : null)
                .subjectId(level != null && level.getSubject() != null ? level.getSubject().getSubjectId() : null)
                .subjectName(level != null && level.getSubject() != null ? level.getSubject().getSubjectName() : null)
                .studentCount(studentCount)
                .googleClassroomId(cls.getGoogleClassroomId())
                .createdAt(cls.getCreatedAt())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Class CRUD
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ClassResponse> getMyClasses(String teacherEmail) {
        User teacher = resolveTeacher(teacherEmail);
        return classRepository.findByTeacherUserId(teacher.getUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ClassResponse createClass(String teacherEmail, ClassCreateRequest request) {
        User teacher = resolveTeacher(teacherEmail);
        SubjectLevel level = subjectLevelRepository.findById(request.getLevelId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trình độ ID: " + request.getLevelId()));

        ClassEntity cls = ClassEntity.builder()
                .className(request.getClassName())
                .courseCode(request.getCourseCode())
                .teacher(teacher)
                .level(level)
                .build();

        return toResponse(classRepository.save(cls));
    }

    @Override
    @Transactional
    public ClassResponse updateClass(String teacherEmail, Integer classId, ClassUpdateRequest request) {
        ClassEntity cls = resolveOwnedClass(teacherEmail, classId);
        SubjectLevel level = subjectLevelRepository.findById(request.getLevelId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trình độ ID: " + request.getLevelId()));

        cls.setClassName(request.getClassName());
        cls.setCourseCode(request.getCourseCode());
        cls.setLevel(level);

        return toResponse(classRepository.save(cls));
    }

    @Override
    @Transactional
    public void deleteClass(String teacherEmail, Integer classId) {
        ClassEntity cls = resolveOwnedClass(teacherEmail, classId);

        // Xóa toàn bộ liên kết học sinh trước
        List<ClassStudent> enrollments = classStudentRepository.findByClassEntity_ClassId(classId);
        classStudentRepository.deleteAll(enrollments);

        classRepository.delete(cls);
    }

    // ─────────────────────────────────────────────────────────────
    // Student management
    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ClassStudentResponse> getStudentsInClass(String teacherEmail, Integer classId) {
        resolveOwnedClass(teacherEmail, classId);  // verify ownership
        return classStudentRepository.findByClassEntity_ClassId(classId)
                .stream()
                .map(cs -> ClassStudentResponse.builder()
                        .studentId(cs.getStudent().getUserId())
                        .fullName(cs.getStudent().getFullName())
                        .email(cs.getStudent().getEmail())
                        .avatarUrl(cs.getStudent().getAvatarUrl())
                        .joinedAt(cs.getJoinedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void addStudentToClass(String teacherEmail, Integer classId, String studentEmail) {
        ClassEntity cls = resolveOwnedClass(teacherEmail, classId);

        User student = userRepository.findByEmailAndRole(studentEmail, Role.STUDENT)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy học sinh với email: " + studentEmail));

        ClassStudentKey key = new ClassStudentKey(classId, student.getUserId());
        if (classStudentRepository.existsById_ClassIdAndId_StudentId(classId, student.getUserId())) {
            throw new IllegalStateException("Học sinh đã có trong lớp này");
        }

        ClassStudent enrollment = ClassStudent.builder()
                .id(key)
                .classEntity(cls)
                .student(student)
                .build();

        classStudentRepository.save(enrollment);
    }

    @Override
    @Transactional
    public void removeStudentFromClass(String teacherEmail, Integer classId, String studentId) {
        resolveOwnedClass(teacherEmail, classId);  // verify ownership

        ClassStudentKey key = new ClassStudentKey(classId, studentId);
        if (!classStudentRepository.existsById(key)) {
            throw new IllegalArgumentException("Học sinh không có trong lớp này");
        }
        classStudentRepository.deleteById(key);
    }
}
