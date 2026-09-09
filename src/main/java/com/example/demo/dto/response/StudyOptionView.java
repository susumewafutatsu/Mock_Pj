package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một lựa chọn của câu hỏi trắc nghiệm khi ôn lại trong sổ tay câu sai.
 *
 * Cố ý KHÔNG có trường {@code isCorrect} — cùng lý do với
 * {@link ExamOptionView} trong phòng thi: gửi đáp án đúng xuống client là
 * dán sẵn lời giải vào mã nguồn trang. Muốn biết đúng hay sai thì gọi
 * {@code POST /mistakes/{questionId}/attempt} và để server trả lời.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyOptionView {

    private Integer answerId;

    private String content;
}
