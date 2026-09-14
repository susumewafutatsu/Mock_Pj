package com.example.demo.domain.enums;

/** Phòng đang ở giai đoạn nào của một buổi thi. */
public enum RoomPhase {

    /** Phòng nháp, chưa ai thấy. */
    DRAFT,

    /** Sảnh chờ: đã mở cho thí sinh vào, chưa tới giờ làm bài. */
    WAITING,

    /** Đang thi: làm bài được, không nhận thêm người. */
    IN_PROGRESS,

    /** Hết giờ hoặc người ra đề đã kết thúc: chỉ còn xem kết quả và xếp hạng. */
    ENDED
}
