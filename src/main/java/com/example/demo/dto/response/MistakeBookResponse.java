package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Một trang của sổ tay câu sai, kèm ba con số tổng quan.
 *
 * Ba con số được trả cùng danh sách chứ không để client tự đếm: client chỉ
 * nhìn thấy một trang, đếm trên đó thì huy hiệu "12 câu cần ôn" sẽ sai ngay
 * khi sang trang hai.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MistakeBookResponse {

    private List<MistakeEntryResponse> items;

    /** Tổng số câu chưa sửa được. */
    private long totalOpen;

    /** Trong đó, bao nhiêu câu đã tới hạn ôn ngay bây giờ. */
    private long totalDue;

    /** Số câu đã sửa được — con số cho thấy mình tiến bộ. */
    private long totalMastered;

    private int page;

    private int size;

    private int totalPages;
}
