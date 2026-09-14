package com.example.demo.domain.enums;

/** Dạng câu hỏi. */
public enum QuestionType {

    /** Chọn một trong các phương án. Dạng của gần như toàn bộ đề JLPT. */
    MULTIPLE_CHOICE,

    /** 並べ替え — sắp xếp câu. Cho bốn mảnh câu. */
    SENTENCE_ORDERING,

    /** Tự luận. KHÔNG đưa được vào đề thi (xem ExamSnapshotServiceImpl) */
    ESSAY
}
