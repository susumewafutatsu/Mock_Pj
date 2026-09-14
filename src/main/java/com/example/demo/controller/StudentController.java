package com.example.demo.controller;

import com.example.demo.dto.request.SaveAnswerRequest;
import com.example.demo.dto.request.SaveAnswersBatchRequest;
import com.example.demo.dto.request.SubmitExamRequest;
import com.example.demo.dto.response.AnswerSavedResponse;
import com.example.demo.dto.response.AnswersBatchSavedResponse;
import com.example.demo.dto.response.ApiResponse;

import com.example.demo.dto.response.ExamResponse;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.ExamSessionResponse;
import com.example.demo.dto.response.HeartbeatResponse;
import com.example.demo.dto.response.LeaderboardResponse;
import com.example.demo.service.LeaderboardService;
import com.example.demo.dto.response.PracticeExamsResponse;
import com.example.demo.dto.response.StudentExamBoardResponse;
import com.example.demo.service.ExamService;

import com.example.demo.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phòng thi của thí sinh. */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final SubmissionService submissionService;
    private final ExamService examService;
    private final LeaderboardService leaderboardService;


    /** Trang chủ: đề của từng phòng thi (đã nhóm sẵn) cộng một phần gợi ý đề luyện tập. */
    @GetMapping("/exams")
    public ApiResponse<StudentExamBoardResponse> examBoard(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(examService.getExamBoard(me.getUsername()));
    }

    /** Toàn bộ bài thi của một phòng. */
    @GetMapping("/rooms/{roomId}/exams")
    public ApiResponse<List<ExamResponse>> roomExams(@PathVariable Integer roomId,
                                                     @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(examService.getRoomExams(roomId, me.getUsername()));
    }

    /** Một trang đề luyện tập tự do, kèm bộ lọc theo trình độ / môn học. */
    @GetMapping("/practice-exams")
    public ApiResponse<PracticeExamsResponse> practiceExams(
            @RequestParam(required = false) Integer levelId,
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(defaultValue = "false") boolean allLevels,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(examService.getPracticeExams(
                levelId, subjectId, allLevels, page, size, me.getUsername()));
    }

    // Danh sách phòng thí sinh đang tham gia nằm ở GET /api/rooms/joined — xem RoomController.

    @PostMapping("/exams/{examId}/start")
    public ApiResponse<ExamSessionResponse> start(@PathVariable Integer examId,
                                                 @AuthenticationPrincipal UserDetails me) {
        ExamSessionResponse session = submissionService.startOrResume(examId, me.getUsername());
        return ApiResponse.success(
                session.isResumed() ? "Tiếp tục phiên thi đang dở" : "Bắt đầu phiên thi",
                session);
    }

    @GetMapping("/exams/{examId}/session")
    public ApiResponse<ExamSessionResponse> session(@PathVariable Integer examId,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.getSession(examId, me.getUsername()));
    }

    @PutMapping("/exams/{examId}/answers")
    public ApiResponse<AnswerSavedResponse> saveAnswer(@PathVariable Integer examId,
                                                       @Valid @RequestBody SaveAnswerRequest request,
                                                       @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.saveAnswer(examId, request, me.getUsername()));
    }

    /** Đường lưu chính của phòng thi: client ghi đáp án vào localStorage trước, rồi đẩy cả lô lên theo nhịp. */
    @PutMapping("/exams/{examId}/answers/batch")
    public ApiResponse<AnswersBatchSavedResponse> saveAnswers(@PathVariable Integer examId,
                                                             @Valid @RequestBody SaveAnswersBatchRequest request,
                                                             @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.saveAnswers(examId, request, me.getUsername()));
    }

    /** Xin phép phát file nghe của một câu; server đếm và chặn khi hết lượt. */
    @PostMapping("/exams/{examId}/questions/{questionId}/audio-play")
    public ApiResponse<com.example.demo.dto.response.AudioPlayResponse> audioPlay(
            @PathVariable Integer examId,
            @PathVariable Integer questionId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.recordAudioPlay(examId, questionId, me.getUsername()));
    }

    @PostMapping("/exams/{examId}/heartbeat")
    public ApiResponse<HeartbeatResponse> heartbeat(@PathVariable Integer examId,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.heartbeat(examId, me.getUsername()));
    }

    /** Nộp bài. Body có thể để trống vì đáp án đã được autosave từ trước. */
    @PostMapping("/exams/{examId}/submit")
    public ApiResponse<ExamResultResponse> submit(@PathVariable Integer examId,
                                                  @Valid @RequestBody(required = false) SubmitExamRequest request,
                                                  @AuthenticationPrincipal UserDetails me) {
        ExamResultResponse result = submissionService.submit(examId, request, me.getUsername());
        return ApiResponse.success(result.isAutoSubmitted()
                ? "Hết giờ, bài đã được nộp tự động" : "Đã nộp bài", result);
    }

    /** Bảng xếp hạng của một đề tự do: tốp đầu + vị trí của chính mình. */
    @GetMapping("/exams/{examId}/leaderboard")
    public ApiResponse<LeaderboardResponse> leaderboard(@PathVariable Integer examId,
                                                        @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(leaderboardService.examLeaderboard(me.getUsername(), examId));
    }

    @GetMapping("/submissions/{submissionId}/result")
    public ApiResponse<ExamResultResponse> result(@PathVariable Integer submissionId,
                                                  @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.getResult(submissionId, me.getUsername()));
    }

    @GetMapping("/results")
    public ApiResponse<List<ExamResultResponse>> history(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.getHistory(me.getUsername()));
    }
}

