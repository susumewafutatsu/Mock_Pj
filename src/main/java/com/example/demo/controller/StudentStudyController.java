package com.example.demo.controller;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.dto.request.MistakeAttemptRequest;
import com.example.demo.dto.request.ReviewGradeRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.DeckResponse;
import com.example.demo.dto.response.MistakeAttemptResponse;
import com.example.demo.dto.response.MistakeBookResponse;
import com.example.demo.dto.response.ReviewQueueResponse;
import com.example.demo.dto.response.ReviewResultResponse;
import com.example.demo.dto.response.StudyStatsResponse;
import com.example.demo.service.MistakeBookService;
import com.example.demo.service.SrsService;
import com.example.demo.service.StudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Góc học tập của thí sinh — tách khỏi {@link StudentController} vì đây là
 * nghiệp vụ HỌC, không phải nghiệp vụ THI.
 *
 * Không endpoint nào ở đây tạo ra hay đụng tới một phiên làm bài. Người học
 * mở app mỗi ngày, ôn thẻ đến hạn và làm lại câu từng sai; chuyện đó diễn ra
 * hằng ngày và không liên quan gì tới đề thi nào cả.
 *
 * Sổ tay câu sai:
 *   GET  /mistakes                      -> hàng đợi câu chưa sửa được
 *   POST /mistakes/{questionId}/attempt -> làm lại một câu, server chấm
 *
 * Thẻ ghi nhớ (từ vựng + chữ Hán):
 *   GET  /decks                         -> các bộ thẻ, kèm tiến độ của tôi
 *   POST /decks/{deckId}/enroll         -> bắt đầu học một bộ
 *   GET  /reviews/due                   -> hàng đợi ôn của hôm nay
 *   POST /reviews/{itemType}/{itemId}   -> tự đánh giá một thẻ vừa lật
 *
 * Tổng quan:
 *   GET  /study/stats                   -> số liệu cho bảng "học hôm nay"
 *
 * Đặt dưới tiền tố {@code /api/student} là có chủ đích: SecurityConfig đã ràng
 * toàn bộ nhánh đó về ROLE_STUDENT, nên không cần và không nên khai lại quyền
 * ở từng phương thức.
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentStudyController {

    private final MistakeBookService mistakeBookService;
    private final SrsService srsService;
    private final StudyService studyService;

    // ── Sổ tay câu sai ──────────────────────────────────────────────────────

    /**
     * Những câu đã làm sai và chưa sửa được, câu cấp thiết nhất xếp trước.
     *
     * Có trả về các lựa chọn để làm lại ngay tại chỗ, nhưng không kèm đáp án
     * đúng — muốn biết đúng sai thì phải trả lời qua endpoint bên dưới.
     */
    @GetMapping("/mistakes")
    public ApiResponse<MistakeBookResponse> mistakes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(
                mistakeBookService.getMistakeBook(me.getUsername(), page, size));
    }

    /**
     * Làm lại một câu trong sổ tay.
     *
     * Đúng hai lần liên tiếp thì câu được đánh dấu đã sửa xong và rời khỏi sổ
     * tay; sai thì nó quay lại hàng đợi ngay trong hôm nay.
     */
    @PostMapping("/mistakes/{questionId}/attempt")
    public ApiResponse<MistakeAttemptResponse> attemptMistake(
            @PathVariable Integer questionId,
            @RequestBody(required = false) MistakeAttemptRequest request,
            @AuthenticationPrincipal UserDetails me) {
        MistakeAttemptResponse result =
                mistakeBookService.attempt(questionId, request, me.getUsername());
        return ApiResponse.success(
                result.isMastered() ? "Đã sửa được câu này"
                        : result.isCorrect() ? "Chính xác" : "Chưa đúng, ôn lại nhé",
                result);
    }

    // ── Thẻ ghi nhớ ─────────────────────────────────────────────────────────

    @GetMapping("/decks")
    public ApiResponse<List<DeckResponse>> decks(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(srsService.listDecks(me.getUsername()));
    }

    /**
     * Bắt đầu học một bộ thẻ. Gọi lại không sao: thẻ đã học giữ nguyên tiến độ,
     * chỉ thẻ chưa có mới được thêm vào lịch.
     */
    @PostMapping("/decks/{deckId}/enroll")
    public ApiResponse<Integer> enrollDeck(@PathVariable Integer deckId,
                                           @AuthenticationPrincipal UserDetails me) {
        int added = srsService.enrollDeck(deckId, me.getUsername());
        return ApiResponse.success(
                added == 0 ? "Bạn đã học hết thẻ của bộ này rồi"
                        : "Đã thêm " + added + " thẻ vào lịch học",
                added);
    }

    /**
     * Hàng đợi ôn của hôm nay.
     *
     * @param limit số thẻ tối đa muốn lấy; bỏ trống thì dùng hạn mức của hệ thống
     */
    @GetMapping("/reviews/due")
    public ApiResponse<ReviewQueueResponse> dueCards(
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(srsService.getDueQueue(me.getUsername(), limit));
    }

    /**
     * Người học vừa lật thẻ và tự đánh giá mình nhớ tới đâu.
     *
     * Trả về lịch mới của thẻ, để màn hình nói được "gặp lại sau 6 ngày".
     */
    @PostMapping("/reviews/{itemType}/{itemId}")
    public ApiResponse<ReviewResultResponse> review(
            @PathVariable StudyItemType itemType,
            @PathVariable Integer itemId,
            @Valid @RequestBody ReviewGradeRequest request,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(
                srsService.review(itemType, itemId, request, me.getUsername()));
    }

    // ── Tổng quan ───────────────────────────────────────────────────────────

    @GetMapping("/study/stats")
    public ApiResponse<StudyStatsResponse> stats(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(studyService.getStats(me.getUsername()));
    }
}
