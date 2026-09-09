package com.example.demo.service;

import com.example.demo.dto.response.StudyStatsResponse;

/**
 * Bảng tổng quan việc học của một thí sinh.
 *
 * Tách riêng khỏi {@link MistakeBookService} và {@link SrsService} vì nó đọc
 * dữ liệu của cả hai: đây là một truy vấn tổng hợp cho màn hình, không thuộc
 * về nghiệp vụ nào trong hai cái kia. Nhét nó vào một trong hai sẽ khiến
 * service đó phải biết về phần nghiệp vụ mà nó không sở hữu.
 */
public interface StudyService {

    /** Số liệu cho bảng "học hôm nay". */
    StudyStatsResponse getStats(String studentEmail);
}
