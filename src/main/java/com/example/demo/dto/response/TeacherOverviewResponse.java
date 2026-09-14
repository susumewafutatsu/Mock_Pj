package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Số liệu cho màn *Tổng quan* của người ra đề. */
@Data
@Builder
public class TeacherOverviewResponse {

    private long totalExams;
    private long publicExams;

    /** Đề chưa gắn câu hỏi nào — thí sinh không vào thi được. Việc cần làm ngay. */
    private long examsWithoutQuestions;

    private long totalRooms;
    /** Phòng đang trong giờ làm bài ngay lúc này. */
    private long roomsInProgress;

    private long totalStudents;

    private long submissionsTotal;
    private long submissionsLast7Days;

    /** Phiên đang làm mà im lặng quá lâu — nghi rớt mạng. */
    private long sessionsAtRisk;

    /** Câu sai nhiều nhất trên toàn bộ đề của người ra đề, tối đa 5 câu. */
    private List<QuestionStatRow> hardestQuestions;

    /** Bài nộp gần nhất, tối đa 8 dòng. */
    private List<TeacherSubmissionRow> recentSubmissions;
}
