package com.example.demo.controller;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.QuestionStatRow;
import com.example.demo.dto.response.TagStatRow;
import com.example.demo.dto.response.TeacherOverviewResponse;
import com.example.demo.dto.response.TeacherStudentRow;
import com.example.demo.dto.response.TeacherSubmissionRow;
import com.example.demo.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Kết quả & phân tích cho người ra đề. */
@RestController
@RequestMapping("/api/teacher/analytics")
@RequiredArgsConstructor
public class TeacherAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ApiResponse<TeacherOverviewResponse> overview(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(analyticsService.overview(me.getUsername()));
    }

    @GetMapping("/exams/{examId}/submissions")
    public ApiResponse<List<TeacherSubmissionRow>> examSubmissions(
            @PathVariable Integer examId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(analyticsService.submissionsOfExam(examId, me.getUsername()));
    }

    @GetMapping("/rooms/{roomId}/submissions")
    public ApiResponse<List<TeacherSubmissionRow>> roomSubmissions(
            @PathVariable Integer roomId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(analyticsService.submissionsOfRoom(roomId, me.getUsername()));
    }

    /** Bài làm đầy đủ của một lượt nộp — màn chữa bài. */
    @GetMapping("/submissions/{submissionId}")
    public ApiResponse<ExamResultResponse> paper(
            @PathVariable Integer submissionId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(analyticsService.paperOfSubmission(submissionId, me.getUsername()));
    }

    /** Tỉ lệ đúng từng câu, câu sai nhiều nhất lên đầu. */
    @GetMapping("/exams/{examId}/questions")
    public ApiResponse<List<QuestionStatRow>> questionStats(
            @PathVariable Integer examId,
            @RequestParam(required = false) Integer roomId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(
                analyticsService.questionStatsOfExam(examId, roomId, me.getUsername()));
    }

    /** Tỉ lệ đúng gộp theo tag (≈ kỹ năng), cùng phạm vi như trên. */
    @GetMapping("/exams/{examId}/tags")
    public ApiResponse<List<TagStatRow>> tagStats(
            @PathVariable Integer examId,
            @RequestParam(required = false) Integer roomId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(
                analyticsService.tagStatsOfExam(examId, roomId, me.getUsername()));
    }

    @GetMapping("/students")
    public ApiResponse<List<TeacherStudentRow>> students(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(analyticsService.students(me.getUsername()));
    }
}
