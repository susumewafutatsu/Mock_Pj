package com.example.demo.service.cache;

import com.example.demo.dto.response.ExamQuestionView;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Vỏ bọc quanh danh sách câu hỏi khi cất vào Redis.
 *
 * Trông thừa nhưng không thừa: Jackson gắn thông tin kiểu bằng thuộc tính
 * {@code "@class"}, mà một JSON array ở gốc thì không có chỗ để gắn thuộc tính.
 * Cất thẳng {@code List<ExamQuestionView>} thì ghi vẫn trôi, tới lúc ĐỌC mới
 * hỏng — nghĩa là lỗi chỉ hiện ra khi cache đã ấm, tức là đang giữa kì thi.
 *
 * Bọc lại thành một object thì gốc có chỗ cho {@code "@class"} và vòng ghi–đọc
 * khép kín. Lớp này chỉ sống trong Redis, không bao giờ đi ra tới client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedExamPaper {

    private List<ExamQuestionView> questions;
}
