package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Một lựa chọn trong bộ lọc của trang đề luyện tập.
 *
 * Danh sách này được dựng từ các trình độ THỰC SỰ có đề luyện tập, không phải
 * từ toàn bộ danh mục trình độ — để học sinh không bấm phải một lựa chọn rồi
 * nhận về danh sách rỗng.
 */
@Data
@Builder
public class PracticeLevelOption {

    private Integer levelId;
    private String levelName;
    private Integer subjectId;
    private String subjectName;

    /** Số đề luyện tập thuộc trình độ này. */
    private long examCount;

    /**
     * Học sinh đang học trình độ này (có lớp thuộc trình độ đó) hay không.
     * Client dùng để đánh dấu "trình độ của bạn" và chọn sẵn khi mở trang.
     */
    private boolean enrolled;
}
