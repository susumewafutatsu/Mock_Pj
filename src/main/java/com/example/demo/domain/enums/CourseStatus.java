package com.example.demo.domain.enums;

/** Vòng đời một khoá học. */
public enum CourseStatus {

    /** Đang soạn. Chỉ tác giả thấy. */
    DRAFT,

    /** Đã gửi duyệt, đang chờ Admin xem. Tác giả không sửa được nữa. */
    PENDING,

    /** Đã duyệt. Thí sinh thấy và học được. */
    PUBLISHED,

    /** Bị từ chối, kèm lý do. Tác giả sửa rồi gửi lại được. */
    REJECTED;

    /** Thí sinh có thấy khoá này không. */
    public boolean isVisibleToLearners() {
        return this == PUBLISHED;
    }

    /** Tác giả còn sửa được nội dung không. */
    public boolean isEditableByAuthor() {
        return this == DRAFT || this == REJECTED || this == PUBLISHED;
    }
}
