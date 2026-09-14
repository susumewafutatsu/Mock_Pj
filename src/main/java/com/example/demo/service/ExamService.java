package com.example.demo.service;

import com.example.demo.dto.response.ExamResponse;
import com.example.demo.dto.response.PracticeExamsResponse;
import com.example.demo.dto.response.StudentExamBoardResponse;

import java.util.List;

/** Đọc đề thi từ phía thí sinh. */
public interface ExamService {

    /** Trang chủ: đề của từng phòng thi, cộng một phần gợi ý đề luyện tập. */
    StudentExamBoardResponse getExamBoard(String studentEmail);

    /** Toàn bộ bài thi của MỘT phòng, kèm trạng thái riêng của thí sinh đang đăng nhập. */
    List<ExamResponse> getRoomExams(Integer roomId, String studentEmail);

    /** Một trang đề luyện tập tự do, kèm bộ lọc để client dựng giao diện chọn trình độ. */
    PracticeExamsResponse getPracticeExams(Integer levelId, Integer subjectId,
                                           boolean allLevels, int page, int size,
                                           String studentEmail);
}
