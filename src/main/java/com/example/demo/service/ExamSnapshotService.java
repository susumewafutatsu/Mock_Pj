package com.example.demo.service;

import com.example.demo.dto.request.ExamQuestionSelection;

import java.util.List;

/**
 * Đóng băng câu hỏi vào đề thi.
 *
 * Đây là ranh giới giữa dữ liệu "sống" (ngân hàng câu hỏi, giáo viên sửa tự do)
 * và dữ liệu "chết" (nội dung + đáp án mà một đề thi cụ thể dùng để hỏi và để
 * chấm). Sau khi snapshot được tạo, mọi thay đổi trong ngân hàng câu hỏi không
 * còn hồi tố lên đề thi đó.
 */
public interface ExamSnapshotService {

    /**
     * Thêm câu hỏi vào đề và chụp snapshot nội dung + toàn bộ đáp án ngay lúc đó.
     * Câu hỏi đã có trong đề sẽ được bỏ qua (không chụp lại).
     *
     * @return số câu hỏi được thêm mới
     */
    int attachQuestions(Integer examId, List<ExamQuestionSelection> selections, String teacherEmail);

    /**
     * Chụp lại snapshot theo nội dung mới nhất trong ngân hàng câu hỏi.
     * Chỉ cho phép khi CHƯA có học sinh nào bắt đầu làm đề — sau mốc đó,
     * thay đổi snapshot sẽ làm điểm đã chấm không còn giải thích được.
     */
    void refreshSnapshot(Integer examId, Integer questionId, String teacherEmail);

    /** Bỏ một câu hỏi khỏi đề, xoá luôn snapshot đáp án của nó. */
    void detachQuestion(Integer examId, Integer questionId, String teacherEmail);
}
