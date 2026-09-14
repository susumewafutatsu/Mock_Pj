package com.example.demo.service;

import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.QuestionStatRow;
import com.example.demo.dto.response.TagStatRow;
import com.example.demo.dto.response.TeacherOverviewResponse;
import com.example.demo.dto.response.TeacherStudentRow;
import com.example.demo.dto.response.TeacherSubmissionRow;

import java.util.List;

/** Thống kê dạy học cho người ra đề. */
public interface AnalyticsService {

    TeacherOverviewResponse overview(String teacherEmail);

    /** Mọi lượt nộp của một đề, mới nhất lên trước. */
    List<TeacherSubmissionRow> submissionsOfExam(Integer examId, String teacherEmail);

    /** Mọi lượt nộp của mọi đề trong một phòng, chỉ tính thành viên của phòng. */
    List<TeacherSubmissionRow> submissionsOfRoom(Integer roomId, String teacherEmail);

    /** Bài làm đầy đủ của một lượt nộp: từng câu, thí sinh chọn gì, đáp án đúng là gì. */
    ExamResultResponse paperOfSubmission(Integer submissionId, String teacherEmail);

    /** Tỉ lệ đúng từng câu của một đề; {@code roomId} khác null thì chỉ tính thí sinh phòng đó. */
    List<QuestionStatRow> questionStatsOfExam(Integer examId, Integer roomId, String teacherEmail);

    /** Tỉ lệ đúng theo tag, cùng phạm vi như trên. */
    List<TagStatRow> tagStatsOfExam(Integer examId, Integer roomId, String teacherEmail);

    /** Thí sinh trong các phòng của người ra đề, kèm tiến độ làm bài. */
    List<TeacherStudentRow> students(String teacherEmail);
}
