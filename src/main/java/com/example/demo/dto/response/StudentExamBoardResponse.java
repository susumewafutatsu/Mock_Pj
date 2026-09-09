package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trang chủ của thí sinh: mọi thứ cần làm, đã tách sẵn thành hai phần.
 *
 * Thay cho danh sách phẳng cũ ở {@code GET /api/student/exams}, vốn trộn đề của
 * mọi phòng với đề luyện tập tự do rồi sắp theo thời gian tạo — thứ tự không nói
 * lên điều gì với người đang cần biết "hôm nay phải làm bài nào".
 *
 * Cấu trúc ở đây phản ánh đúng hai câu hỏi khác nhau của thí sinh:
 * {@link #rooms} trả lời "phòng tôi tham gia giao bài gì", {@link #practice} trả lời "tự ôn
 * thì làm đề nào".
 */
@Data
@Builder
public class StudentExamBoardResponse {

    /** Bài thi theo từng phòng thí sinh đang tham gia. Rỗng nếu chưa vào phòng nào. */
    private List<RoomExamGroup> rooms;

    /**
     * Gợi ý đề luyện tập theo trình độ của các phòng đang tham gia — chỉ là bản xem
     * trước. Danh sách đầy đủ kèm bộ lọc nằm ở {@code GET /practice-exams}.
     */
    private List<ExamResponse> practice;

    /** Tổng số bài thi còn phải làm trên tất cả các phòng. */
    private int pendingCount;

    /** true nếu {@link #practice} đã bị cắt bớt so với số đề thực có. */
    private boolean practiceTruncated;

    private LocalDateTime serverTime;
}
