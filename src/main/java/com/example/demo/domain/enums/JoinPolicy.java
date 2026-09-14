package com.example.demo.domain.enums;

/** Cách một người vào được phòng thi. */
public enum JoinPolicy {

    /** Ai thấy phòng cũng vào được. Dùng cho phòng luyện tập mở. */
    OPEN,

    /** Phải có mã phòng. Mặc định — đủ kín cho một buổi kiểm tra bình thường. */
    CODE,

    /** Xin vào rồi chờ duyệt. */
    APPROVAL
}
