package com.example.demo.domain.enums;

/**
 * Tư cách của một người trong phòng thi.
 *
 * Rời phòng hay bị mời ra đều KHÔNG xoá dòng thành viên, chỉ đổi trạng thái.
 * Xoá cứng thì mất dấu ai đã từng ở trong phòng — và quan trọng hơn, ghế của
 * người đó sẽ được cấp lại cho người khác, khiến số ghế không còn khớp với
 * lịch sử làm bài.
 */
public enum MemberStatus {

    /** Đang trong phòng. */
    ACTIVE,

    /** Tự rời đi. */
    LEFT,

    /** Bị người ra đề mời ra. */
    KICKED
}
