package com.example.demo.service;

import com.example.demo.domain.model.User;
import com.example.demo.dto.request.MistakeAttemptRequest;
import com.example.demo.dto.response.MistakeAttemptResponse;
import com.example.demo.dto.response.MistakeBookResponse;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Sổ tay câu sai — hàng đợi ôn lại những câu thí sinh từng làm sai.
 *
 * Vì sao tách khỏi {@link SubmissionService}: bài làm là chuyện của một lần
 * thi, còn sổ tay là chuyện của cả quá trình học. Một câu sai ở ba đề khác
 * nhau vẫn chỉ là một mục trong sổ tay, và việc ôn lại nó không sinh ra bài
 * làm nào cả.
 */
public interface MistakeBookService {

    /**
     * Ghi nhận những câu vừa làm sai vào sổ tay của thí sinh.
     *
     * Gọi ngay sau khi chấm xong một bài. Idempotent theo cặp (người, câu hỏi):
     * gọi lại với cùng dữ liệu chỉ làm tăng số lần sai chứ không tạo dòng trùng.
     *
     * @param student        chủ sổ tay
     * @param wrongQuestionIds các câu vừa bị chấm là sai
     * @param now            giờ server tại thời điểm chấm
     * @return số câu được ghi nhận
     */
    int recordMistakes(User student, Collection<Integer> wrongQuestionIds, LocalDateTime now);

    /** Một trang sổ tay, câu cấp thiết nhất xếp trước. */
    MistakeBookResponse getMistakeBook(String studentEmail, int page, int size);

    /**
     * Làm lại một câu trong sổ tay.
     *
     * Đây cũng là chỗ duy nhất trả về đáp án đúng và lời giải, và chỉ trả sau
     * khi người học đã chọn xong.
     */
    MistakeAttemptResponse attempt(Integer questionId, MistakeAttemptRequest request,
                                   String studentEmail);
}
