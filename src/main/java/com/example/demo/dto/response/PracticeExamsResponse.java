package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Trang đề luyện tập tự do. */
@Data
@Builder
public class PracticeExamsResponse {

    /** Các trình độ có thể lọc, kèm số đề và dấu "trình độ bạn đang học". */
    private List<PracticeLevelOption> levels;

    /** Bộ lọc đang áp dụng — echo lại để client không phải tự nhớ trạng thái. */
    private Integer appliedLevelId;
    private Integer appliedSubjectId;

    /** true khi thí sinh không tự chọn gì và server đã chọn hộ một trình độ (lấy theo phòng em đang tham gia). */
    private boolean filteredByEnrolledLevels;

    /** Đề của TRANG hiện tại, không phải toàn bộ kết quả. */
    private List<ExamResponse> exams;

    // ── Phân trang ──────────────────────────────────────────────────────────

    /** Trang hiện tại, đánh số từ 0 (giống Spring Data). */
    private int page;

    /** Số đề mỗi trang, sau khi server đã kẹp về khoảng cho phép. */
    private int size;

    /** Tổng số đề khớp bộ lọc — để client hiện "24 đề" chứ không phải "12 đề". */
    private long totalElements;

    /** Tổng số trang. 0 khi không có đề nào khớp. */
    private int totalPages;

    /** Giờ server — cùng mục đích với trường cùng tên trong ExamResponse. */
    private LocalDateTime serverTime;
}
