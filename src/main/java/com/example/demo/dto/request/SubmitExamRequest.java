package com.example.demo.dto.request;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Nộp bài.
 *
 * Vì mỗi lần chọn đáp án đã được autosave ngay, request này thường không cần
 * mang theo dữ liệu gì. Danh sách {@code answers} chỉ là lưới an toàn cho các
 * câu mà lần autosave cuối chưa kịp gửi lên (mạng chập lúc bấm nộp) — server
 * upsert chúng trước khi chấm, bằng đúng đường đi của autosave.
 */
@Data
public class SubmitExamRequest {

    @Valid
    private List<SaveAnswerRequest> answers = new ArrayList<>();
}
