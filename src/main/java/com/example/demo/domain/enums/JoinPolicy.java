package com.example.demo.domain.enums;

/**
 * Cách một người vào được phòng thi.
 *
 * Đây là thứ thay cho việc người ra đề thêm từng thí sinh vào lớp học: thí sinh tự
 * vào, còn người ra đề chỉ quyết định cửa mở tới đâu.
 */
public enum JoinPolicy {

    /** Ai thấy phòng cũng vào được. Dùng cho phòng luyện tập mở. */
    OPEN,

    /** Phải có mã phòng. Mặc định — đủ kín cho một buổi kiểm tra bình thường. */
    CODE,

    /**
     * Xin vào rồi chờ duyệt.
     *
     * Chưa cài đặt luồng duyệt; khai sẵn để migration từ lớp cũ có chỗ ánh xạ
     * và để không phải sửa enum (kéo theo sửa cột) khi làm tới.
     */
    APPROVAL
}
