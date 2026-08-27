package com.example.demo.service;

import com.example.demo.dto.request.QuestionCreateRequest;
import com.example.demo.dto.request.QuestionUpdateRequest;
import com.example.demo.dto.response.QuestionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Quản lý câu hỏi trong ngân hàng — nghiệp vụ của giáo viên.
 *
 * Mọi phương thức nhận {@code teacherEmail} (lấy từ token) và tự kiểm tra
 * quyền sở hữu ngân hàng câu hỏi: chỉ role TEACHER là chưa đủ, giáo viên A
 * không được chạm vào ngân hàng của giáo viên B.
 */
public interface QuestionService {

    QuestionResponse create(QuestionCreateRequest request, String teacherEmail);

    /**
     * Sửa câu hỏi. Không chặn dù câu hỏi đã nằm trong đề thi đã phát hành:
     * các đề đó đọc snapshot của riêng chúng
     * ({@link com.example.demo.domain.model.ExamQuestion}), nên bài đã nộp và
     * điểm đã chấm không bị ảnh hưởng.
     */
    QuestionResponse update(Integer bankId, Integer questionId,
                            QuestionUpdateRequest request, String teacherEmail);

    /**
     * Xoá câu hỏi khỏi ngân hàng. Nếu câu hỏi đã được dùng trong đề thi thì
     * chỉ xoá mềm để giữ lịch sử và liên kết thống kê.
     */
    void delete(Integer bankId, Integer questionId, String teacherEmail);

    Page<QuestionResponse> listByBank(Integer bankId, String teacherEmail, Pageable pageable);

    QuestionResponse getOne(Integer bankId, Integer questionId, String teacherEmail);

    /** Dùng cho adaptive engine: lọc câu hỏi theo khoảng độ khó. */
    List<QuestionResponse> filterByDifficulty(Integer bankId, int minDifficulty,
                                              int maxDifficulty, String teacherEmail);
}
