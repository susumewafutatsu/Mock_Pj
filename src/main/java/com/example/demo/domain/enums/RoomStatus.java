package com.example.demo.domain.enums;

/** Vòng đời một phòng thi — thứ người ra đề bấm nút để đổi. */
public enum RoomStatus {

    /** Đang soạn, chưa ai thấy. Người ra đề còn đang gắn đề và chỉnh sức chứa. */
    DRAFT,

    /** Đã mở cho thí sinh vào phòng — sảnh chờ. */
    OPEN,

    /** Người ra đề đã bấm "Bắt đầu làm bài": thí sinh làm bài được, phòng không nhận thêm người. */
    RUNNING,

    /** Đã đóng. Không vào được, không làm bài được, chỉ còn xem kết quả. */
    CLOSED
}
