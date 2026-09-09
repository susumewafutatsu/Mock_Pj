package com.example.demo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một lần làm lại câu sai trong sổ tay.
 *
 * Hai đường vào vì hai loại câu hỏi được chấm theo hai cách khác nhau:
 *
 *   - Trắc nghiệm: gửi {@link #selectedAnswerId}, server tra bảng Answers và
 *     tự quyết định đúng sai. Client không có quyền nói mình đúng.
 *   - Tự luận: không có đáp án để máy so, nên người học tự chấm qua
 *     {@link #selfCorrect}. Đây là ôn tập cá nhân, không phải bài thi tính
 *     điểm, nên tự đánh giá là hợp lý — gian lận ở đây chỉ hại chính mình.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MistakeAttemptRequest {

    /** Bắt buộc với câu trắc nghiệm. */
    private Integer selectedAnswerId;

    /** Chỉ dùng cho câu tự luận. Bỏ trống thì coi như chưa nhớ được. */
    private Boolean selfCorrect;
}
