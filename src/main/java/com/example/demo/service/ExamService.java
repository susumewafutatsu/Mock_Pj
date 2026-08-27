package com.example.demo.service;

import com.example.demo.dto.response.ExamResponse;

import java.util.List;

/**
 * Đọc đề thi từ phía học sinh.
 *
 * Chỉ có phần "chọn đề để làm". Toàn bộ vòng đời một phiên làm bài (bắt đầu,
 * autosave, heartbeat, nộp, chấm) nằm ở {@link SubmissionService}; tạo và sửa
 * đề là việc của module giáo viên.
 *
 * TODO (module giáo viên, chưa làm): createExam, deleteExam, getExamsByClass,
 * và selectNextQuestion cho chế độ adaptive.
 */
public interface ExamService {

    /**
     * Danh sách đề mà học sinh này được làm, kèm trạng thái của riêng em đó
     * (chưa mở / đang mở / đang làm dở / đã nộp / đã đóng).
     *
     * Phạm vi hiển thị: đề của các lớp học sinh có tên trong danh sách, cộng
     * các đề luyện tập tự do (không gắn lớp). Đề của lớp khác không xuất hiện —
     * đây là cùng một luật với {@code requireEnrolled} lúc bắt đầu thi, nên
     * không có đề nào nhìn thấy được mà bấm vào lại bị chặn.
     *
     * Không kèm câu hỏi: nội dung đề chỉ mở khi gọi start.
     */
    List<ExamResponse> getExamsForStudent(String studentEmail);
}
