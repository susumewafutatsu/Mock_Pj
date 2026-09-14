package com.example.demo.service;

import com.example.demo.domain.model.User;
import com.example.demo.dto.request.MistakeAttemptRequest;
import com.example.demo.dto.response.MistakeAttemptResponse;
import com.example.demo.dto.response.MistakeBookResponse;

import java.time.LocalDateTime;
import java.util.Collection;

/** Sổ tay câu sai — hàng đợi ôn lại những câu thí sinh từng làm sai. */
public interface MistakeBookService {

    /** Ghi nhận những câu vừa làm sai vào sổ tay của thí sinh. */
    int recordMistakes(User student, Collection<Integer> wrongQuestionIds, LocalDateTime now);

    /** Một trang sổ tay, câu cấp thiết nhất xếp trước. */
    MistakeBookResponse getMistakeBook(String studentEmail, int page, int size);

    /** Làm lại một câu trong sổ tay. */
    MistakeAttemptResponse attempt(Integer questionId, MistakeAttemptRequest request,
                                   String studentEmail);
}
