package com.example.demo.controller;

import com.example.demo.dto.request.SaveAnswerRequest;
import com.example.demo.dto.request.SubmitExamRequest;
import com.example.demo.dto.response.AnswerSavedResponse;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.dto.response.ExamResponse;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.ExamSessionResponse;
import com.example.demo.dto.response.HeartbeatResponse;
import com.example.demo.dto.response.PracticeExamsResponse;
import com.example.demo.dto.response.StudentExamBoardResponse;
import com.example.demo.service.ExamService;
import com.example.demo.service.StudentClassService;
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

/**
 * Phòng thi của học sinh.
 *
 * Tìm đề — ba lối vào cho ba màn hình khác nhau:
 *   GET    /exams                   -> trang chủ: đề nhóm theo lớp + gợi ý đề luyện tập
 *   GET    /classes                 -> các lớp đang học
 *   GET    /classes/{id}/exams      -> toàn bộ đề của một lớp
 *   GET    /practice-exams          -> đề luyện tập tự do, kèm bộ lọc trình độ
 *
 * Thứ tự client gọi trong một phiên làm bài bình thường:
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
    private final StudentClassService studentClassService;

    /**
     * Trang chủ: đề của từng lớp (đã nhóm sẵn) cộng một phần gợi ý đề luyện tập.
     *
     * Bản trước trả một danh sách phẳng trộn cả hai loại đề — xem
     * {@link ExamService} để biết vì sao đã tách ra.

    /**
     * Danh sách đề học sinh được làm: đề của các lớp em đang học, cộng đề luyện
     * tập tự do. Mỗi dòng kèm trạng thái riêng của em đó (đang mở / đang làm dở
     * / đã nộp / đã đóng) nên client không phải tự so mốc thời gian.
     *
     * Không trả câu hỏi — nội dung đề chỉ mở ra ở endpoint start.
     */
    @GetMapping("/exams")
    public ApiResponse<StudentExamBoardResponse> examBoard(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(examService.getExamBoard(me.getUsername()));
    }

    /**
     * Toàn bộ đề của một lớp. Trả 404 nếu học sinh không học lớp đó — không tiết
     * lộ lớp có tồn tại hay không cho người ngoài.
     */
    @GetMapping("/classes/{classId}/exams")
    public ApiResponse<List<ExamResponse>> classExams(@PathVariable Integer classId,
                                                      @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(examService.getClassExams(classId, me.getUsername()));
    }

    /**
     * Một trang đề luyện tập tự do, kèm bộ lọc theo trình độ / môn học.
     *
     * Không truyền bộ lọc thì server chọn hộ một trình độ theo lớp học sinh đang
     * học và bật cờ {@code filteredByEnrolledLevels}, để client hiện được lối
     * thoát "xem tất cả trình độ".
     *
     * {@code page} đánh số từ 0. {@code size} bị server kẹp về khoảng cho phép.
     */
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

    /**
     * Danh sách lớp học mà học sinh đang đăng ký — cửa vào của
     * {@code GET /classes/{classId}/exams}.
     */
    @GetMapping("/classes")
    public ApiResponse<List<ClassResponse>> myClasses(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Lấy danh sách lớp học thành công",
                studentClassService.getMyClasses(me.getUsername()));
    }

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

