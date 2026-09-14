package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Một phần thi trong phiên làm bài của thí sinh. */
@Data
@Builder
public class ExamSectionView {

    private Integer sectionId;
    private String name;
    private Integer orderNo;
    private Integer durationMinutes;

    /** Mốc phần này mở, theo giờ server. */
    private LocalDateTime startsAt;

    /** Mốc phần này khoá, theo giờ server. */
    private LocalDateTime endsAt;

    /** Phần đang được làm ngay lúc trả response. */
    private boolean current;

    /** Đã hết giờ — chỉ xem lại được, không sửa đáp án nữa. */
    private boolean locked;

    /** Chưa tới lượt. Client hiện phần này ở dạng chờ, không cho mở. */
    private boolean upcoming;

    /** Giây còn lại của phần này. 0 khi đã khoá hoặc chưa mở. */
    private long remainingSeconds;

    private int totalQuestions;
    private int answeredQuestions;
}
