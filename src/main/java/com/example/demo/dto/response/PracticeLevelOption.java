package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

/** Một lựa chọn trong bộ lọc của trang đề luyện tập. */
@Data
@Builder
public class PracticeLevelOption {

    private Integer levelId;
    private String levelName;
    private Integer subjectId;
    private String subjectName;

    /** Số đề luyện tập thuộc trình độ này. */
    private long examCount;

    /** Thí sinh đang học trình độ này (có phòng thuộc trình độ đó) hay không. */
    private boolean enrolled;
}
