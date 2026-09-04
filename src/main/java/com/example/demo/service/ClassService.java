package com.example.demo.service;

import com.example.demo.dto.request.ClassCreateRequest;
import com.example.demo.dto.request.ClassUpdateRequest;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.dto.response.ClassStudentResponse;

import java.util.List;

/**
 * Quản lý lớp học phía Giáo viên.
 *
 * Tất cả phương thức đều nhận teacherEmail lấy từ JWT để đảm bảo
 * giáo viên chỉ thao tác được trên lớp của chính mình.
 */
public interface ClassService {

    /** Danh sách lớp của giáo viên đang đăng nhập */
    List<ClassResponse> getMyClasses(String teacherEmail);

    /** Tạo lớp mới cho giáo viên */
    ClassResponse createClass(String teacherEmail, ClassCreateRequest request);

    /** Cập nhật thông tin lớp — chỉ chủ lớp mới được sửa */
    ClassResponse updateClass(String teacherEmail, Integer classId, ClassUpdateRequest request);

    /** Xóa lớp — chỉ chủ lớp mới được xóa, không cho xóa khi còn học sinh */
    void deleteClass(String teacherEmail, Integer classId);

    /** Danh sách học sinh trong lớp */
    List<ClassStudentResponse> getStudentsInClass(String teacherEmail, Integer classId);

    /** Thêm học sinh vào lớp bằng email */
    void addStudentToClass(String teacherEmail, Integer classId, String studentEmail);

    /** Xóa học sinh khỏi lớp */
    void removeStudentFromClass(String teacherEmail, Integer classId, String studentId);
}
