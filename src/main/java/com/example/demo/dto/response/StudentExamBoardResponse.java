package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trang chủ của học sinh: mọi thứ cần làm, đã tách sẵn thành hai phần.
 *
 * Thay cho danh sách phẳng cũ ở {@code GET /api/student/exams}, vốn trộn đề của
 * mọi lớp với đề luyện tập tự do rồi sắp theo thời gian tạo — thứ tự không nói
 * lên điều gì với người đang cần biết "hôm nay phải làm bài nào".
 *
 * Cấu trúc ở đây phản ánh đúng hai câu hỏi khác nhau của học sinh:
 * {@link #classes} trả lời "cô giao bài gì", {@link #practice} trả lời "tự ôn
 * thì làm đề nào".
 */
@Data
@Builder
public class StudentExamBoardResponse {

    /** Đề theo từng lớp học sinh đang học. Rỗng nếu em chưa vào lớp nào. */
    private List<ClassExamGroup> classes;

    /**
     * Gợi ý đề luyện tập theo trình độ của các lớp đang học — chỉ là bản xem
     * trước. Danh sách đầy đủ kèm bộ lọc nằm ở {@code GET /practice-exams}.
     */
    private List<ExamResponse> practice;

    /** Tổng số đề còn phải làm trên tất cả các lớp. */
    private int pendingCount;

    /** true nếu {@link #practice} đã bị cắt bớt so với số đề thực có. */
    private boolean practiceTruncated;

    private LocalDateTime serverTime;
}
