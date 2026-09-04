package com.example.demo.service;

import com.example.demo.dto.request.ExamCreateRequest;
import com.example.demo.dto.response.TeacherExamResponse;

import java.util.List;

/**
 * Tạo và quản lý đề thi phía Giáo viên.
 *
 * Mọi phương thức nhận teacherEmail lấy từ JWT: giáo viên chỉ thao tác được
 * trên đề do chính mình tạo, và chỉ gắn đề vào lớp của chính mình.
 */
public interface TeacherExamService {

    /** Đề thi do giáo viên này tạo, mới nhất trước. */
    List<TeacherExamResponse> listMine(String teacherEmail);

    /** Tạo đề mới. Câu hỏi gắn sau qua API riêng. */
    TeacherExamResponse create(String teacherEmail, ExamCreateRequest request);

    /**
     * Sửa đề. Không cho sửa khi đã có học sinh bắt đầu làm — đổi thời lượng hay
     * lớp lúc đó sẽ làm sai phiên thi đang chạy.
     */
    TeacherExamResponse update(String teacherEmail, Integer examId, ExamCreateRequest request);

    /** Xóa đề. Không cho xóa khi đã có học sinh làm bài. */
    void delete(String teacherEmail, Integer examId);
}
