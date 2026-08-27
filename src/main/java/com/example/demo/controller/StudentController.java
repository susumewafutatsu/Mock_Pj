package com.example.demo.controller;

import com.example.demo.dto.request.SaveAnswerRequest;
import com.example.demo.dto.request.SubmitExamRequest;
import com.example.demo.dto.response.AnswerSavedResponse;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.ExamResponse;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.ExamSessionResponse;
import com.example.demo.dto.response.HeartbeatResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phòng thi của học sinh.
 *
 * Thứ tự client gọi trong một phiên bình thường:
 *   GET    /exams                   -> danh sách đề được làm, kèm trạng thái từng đề
 *   POST   /exams/{id}/start        -> lấy đề + expiresAt (gọi lại = vào lại, không tạo phiên mới)
 *   PUT    /exams/{id}/answers      -> mỗi lần chọn đáp án, gọi ngay (autosave)
 *   POST   /exams/{id}/heartbeat    -> 15-30 giây một lần, để server biết còn sống
 *   POST   /exams/{id}/submit       -> nộp bài
 *   GET    /submissions/{id}/result -> xem điểm
 *
 * Sau khi mất mạng, client gọi GET /exams/{id}/session để lấy lại toàn bộ đáp án
 * đã lưu và thời gian còn lại tính theo giờ server.
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final SubmissionService submissionService;
    private final ExamService examService;

    /**
     * Danh sách đề học sinh được làm: đề của các lớp em đang học, cộng đề luyện
     * tập tự do. Mỗi dòng kèm trạng thái riêng của em đó (đang mở / đang làm dở
     * / đã nộp / đã đóng) nên client không phải tự so mốc thời gian.
     *
     * Không trả câu hỏi — nội dung đề chỉ mở ra ở endpoint start.
     */
    @GetMapping("/exams")
    public ApiResponse<List<ExamResponse>> exams(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(examService.getExamsForStudent(me.getUsername()));
    }

    /**
     * Vào phòng thi. Idempotent: gọi bao nhiêu lần cũng chỉ có một phiên thi,
     * lần sau trả về đúng phiên đang dở kèm các đáp án đã chọn.
     * Trả 409 nếu học sinh đã nộp bài đề này, đề chưa mở / đã đóng, hoặc phiên
     * đang dở đã hết giờ (bài được nộp tự động trước khi báo lỗi).
     */
    @PostMapping("/exams/{examId}/start")
    public ApiResponse<ExamSessionResponse> start(@PathVariable Integer examId,
                                                 @AuthenticationPrincipal UserDetails me) {
        ExamSessionResponse session = submissionService.startOrResume(examId, me.getUsername());
        return ApiResponse.success(
                session.isResumed() ? "Tiếp tục phiên thi đang dở" : "Bắt đầu phiên thi",
                session);
    }

    /**
     * Khôi phục phiên sau khi mất kết nối. Không tạo phiên mới — trả 404 nếu
     * học sinh chưa từng bắt đầu đề này.
     */
    @GetMapping("/exams/{examId}/session")
    public ApiResponse<ExamSessionResponse> session(@PathVariable Integer examId,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.getSession(examId, me.getUsername()));
    }

    /**
     * Autosave một câu trả lời. Gọi ngay khi học sinh bấm chọn, không đợi nộp bài.
     * Gửi lại cùng một câu nhiều lần là an toàn (upsert).
     */
    @PutMapping("/exams/{examId}/answers")
    public ApiResponse<AnswerSavedResponse> saveAnswer(@PathVariable Integer examId,
                                                       @Valid @RequestBody SaveAnswerRequest request,
                                                       @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(submissionService.saveAnswer(examId, request, me.getUsername()));
    }

    /**
     * Nhịp sống của client, gọi mỗi 15-30 giây. Chỉ cập nhật LastActiveAt để
     * phát hiện rớt mạng — KHÔNG cộng bù giờ cho thời gian mất kết nối.
     */
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
