package com.example.demo.domain.enums;

/**
 * Vòng đời một khoá học.
 *
 * Có bốn trạng thái vì việc soạn nội dung và việc chịu trách nhiệm về nội dung
 * thuộc về hai người khác nhau: người ra đề soạn, Admin duyệt. Xem
 * revision_plan.md §3.4(c) về lý do không để bên nào tự làm cả hai.
 */
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

    /**
     * Tác giả còn sửa được nội dung không.
     *
     * PENDING thì không: đang chờ người khác xem thì nội dung phải đứng yên, nếu
     * không Admin duyệt một bản còn tác giả đã đổi sang bản khác.
     */
    public boolean isEditableByAuthor() {
        return this == DRAFT || this == REJECTED || this == PUBLISHED;
    }
}
