package com.example.demo.service;

import com.example.demo.dto.response.ExamResponse;
import com.example.demo.dto.response.PracticeExamsResponse;
import com.example.demo.dto.response.StudentExamBoardResponse;

import java.util.List;

/**
 * Đọc đề thi từ phía thí sinh.
 *
 * Chỉ có phần "chọn đề để làm". Toàn bộ vòng đời một phiên làm bài (bắt đầu,
 * autosave, heartbeat, nộp, chấm) nằm ở {@link SubmissionService}; tạo và sửa
 * đề là việc của module người ra đề.
 *
 * <h2>Vì sao có ba phương thức thay vì một</h2>
 *
 * Thí sinh gặp đề thi qua hai đường hoàn toàn khác nhau:
 *
 * <ul>
 *   <li><b>Đề của phòng thi</b> — người ra đề giao, có hạn nộp, bắt buộc. Thí sinh tìm
 *       nó bằng cách vào lớp.</li>
 *   <li><b>Đề luyện tập tự do</b> — không thuộc phòng nào, thí sinh tự chọn để
 *       ôn. Tìm bằng cách lọc theo trình độ, không phải bằng cách nhớ tên.</li>
 * </ul>
 *
 * Bản cũ đổ cả hai vào một danh sách phẳng sắp theo thời gian tạo, nên không
 * màn hình nào dùng được đúng: trang phòng thi phải tự lọc lại, trang luyện tập không
 * có bộ lọc, và không có trường nào phân biệt hai loại ngoài việc đoán theo
 * {@code className == null}.
 *
 * Cả ba phương thức đều KHÔNG trả câu hỏi — nội dung đề chỉ mở ra ở
 * {@code POST /api/student/exams/{id}/start}.
 *
 * TODO (module người ra đề, chưa làm): selectNextQuestion cho chế độ adaptive.
 */
public interface ExamService {

    /**
     * Trang chủ: đề của từng phòng thi, cộng một phần gợi ý đề luyện tập.
     *
     * Dùng cho màn hình tổng quan. Muốn danh sách đầy đủ của một lớp hay của
     * phần luyện tập thì gọi hai phương thức dưới — chúng không bị cắt bớt.
     */
    StudentExamBoardResponse getExamBoard(String studentEmail);

    /**
     * Toàn bộ bài thi của MỘT phòng, kèm trạng thái riêng của thí sinh đang đăng nhập.
     *
     * @throws com.example.demo.exception.ResourceNotFoundException nếu phòng không
     *         tồn tại HOẶC thí sinh không ở trong phòng đó. Trả 404 chứ không
     *         403 để không tiết lộ phòng có tồn tại hay không cho người ngoài —
     *         cùng một luật với {@code requireEnrolled} lúc bắt đầu thi.
     */
    List<ExamResponse> getRoomExams(Integer roomId, String studentEmail);

    /**
     * Một trang đề luyện tập tự do, kèm bộ lọc để client dựng giao diện chọn
     * trình độ.
     *
     * Không truyền bộ lọc thì server chọn hộ một trình độ — trình độ đầu tiên
     * của phòng thí sinh đang tham gia mà thực sự có đề luyện tập — và bật cờ
     * {@code filteredByEnrolledLevels}. Đây là điểm khởi đầu hữu ích chứ không
     * phải giới hạn: thí sinh chọn trình độ khác vẫn xem được, và em chưa vào
     * phòng nào thì thấy toàn bộ đề thay vì thấy một danh sách rỗng.
     *
     * @param allLevels thí sinh CHỦ ĐỘNG chọn "xem tất cả trình độ". Cần một cờ
     *                  riêng vì "không gửi bộ lọc" mang hai nghĩa khác nhau:
     *                  vừa mở trang (để server chọn hộ), hay vừa bấm "Tất cả"
     *                  (đừng chọn hộ nữa). Thiếu cờ này thì nút "Tất cả" bấm
     *                  vào lại quay về đúng trình độ mà server vừa chọn.
     * @param page trang, đánh số từ 0. Số âm được kẹp về 0.
     * @param size số đề mỗi trang. Được kẹp về khoảng cho phép để một request
     *             {@code ?size=100000} không kéo cả bảng về.
     */
    PracticeExamsResponse getPracticeExams(Integer levelId, Integer subjectId,
                                           boolean allLevels, int page, int size,
                                           String studentEmail);
}
