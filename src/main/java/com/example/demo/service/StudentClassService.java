package com.example.demo.service;

import com.example.demo.dto.response.ClassResponse;

import java.util.List;

/**
 * Lớp học nhìn từ phía học sinh — chỉ đọc, và chỉ những lớp em có tên trong đó.
 *
 * Tách khỏi {@link ClassService} (vốn là module quản lý lớp của giáo viên) vì
 * hai bên khác nhau cả về quyền lẫn về thứ cần hiển thị: giáo viên sửa lớp và
 * xem danh sách học sinh, học sinh chỉ xem lớp mình học để mở tới đề của lớp.
 *
 * Trước đây phần này nằm thẳng trong {@code StudentController} — controller vừa
 * gọi repository vừa dựng DTO, và dựng bằng cách load toàn bộ danh sách học sinh
 * của mỗi lớp chỉ để lấy ra thông tin lớp.
 */
public interface StudentClassService {

    /**
     * Các lớp học sinh đang học, kèm môn / trình độ / giáo viên / sĩ số.
     *
     * Đây là cửa vào của {@link ExamService#getClassExams} — client lấy classId
     * từ danh sách này rồi mở đề của lớp tương ứng.
     */
    List<ClassResponse> getMyClasses(String studentEmail);
}
