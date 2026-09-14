package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Trang chủ của thí sinh: mọi thứ cần làm, đã tách sẵn thành hai phần. */
@Data
@Builder
public class StudentExamBoardResponse {

    /** Bài thi theo từng phòng thí sinh đang tham gia. Rỗng nếu chưa vào phòng nào. */
    private List<RoomExamGroup> rooms;

    /** Gợi ý đề luyện tập theo trình độ của các phòng đang tham gia — chỉ là bản xem trước. */
    private List<ExamResponse> practice;

    /** Tổng số bài thi còn phải làm trên tất cả các phòng. */
    private int pendingCount;

    /** true nếu {@link #practice} đã bị cắt bớt so với số đề thực có. */
    private boolean practiceTruncated;

    private LocalDateTime serverTime;
}
