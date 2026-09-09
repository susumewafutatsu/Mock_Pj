package com.example.demo.domain.enums;

/**
 * Vòng đời một phòng thi.
 *
 * Trạng thái quyết định hai việc khác nhau, đừng lẫn: ai được VÀO phòng, và ai
 * được LÀM BÀI trong phòng.
 */
public enum RoomStatus {

    /** Đang soạn, chưa ai thấy. Người ra đề còn đang gắn đề và chỉnh sức chứa. */
    DRAFT,

    /** Đã mở, thí sinh vào được và làm bài được. */
    OPEN,

    /**
     * Đang thi: không nhận người mới nữa, nhưng ai đã ở trong vẫn làm bài được.
     *
     * Có trạng thái này để người ra đề chốt danh sách đúng lúc bắt đầu mà không
     * phải đóng cả phòng — đóng phòng là cắt luôn bài của người đang làm dở.
     */
    RUNNING,

    /** Đã đóng. Không vào được, không làm bài được, chỉ còn xem lại kết quả. */
    CLOSED;

    /** Thí sinh mới có xin vào được không. */
    public boolean acceptsNewMembers() {
        return this == OPEN;
    }

    /** Thành viên trong phòng có làm bài được không. */
    public boolean allowsTakingExam() {
        return this == OPEN || this == RUNNING;
    }
}
