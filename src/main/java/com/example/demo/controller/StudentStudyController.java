package com.example.demo.controller;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.dto.request.CustomCardRequest;
import com.example.demo.dto.request.DeckItemRequest;
import com.example.demo.dto.request.DeckRequest;
import com.example.demo.dto.request.MistakeAttemptRequest;
import com.example.demo.dto.request.ReviewGradeRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.DeckCardView;
import com.example.demo.dto.response.DeckDetailResponse;
import com.example.demo.dto.response.DeckResponse;
import com.example.demo.dto.response.StudyContentHit;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Góc học tập của thí sinh — tách khỏi {@link StudentController} vì đây là nghiệp vụ HỌC. */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentStudyController {

    private final MistakeBookService mistakeBookService;
    private final SrsService srsService;
    private final StudyService studyService;

    // ── Sổ tay câu sai ──────────────────────────────────────────────────────

    /** Những câu đã làm sai và chưa sửa được, câu cấp thiết nhất xếp trước. */
    @GetMapping("/mistakes")
    public ApiResponse<MistakeBookResponse> mistakes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(
                mistakeBookService.getMistakeBook(me.getUsername(), page, size));
    }

    /** Làm lại một câu trong sổ tay. */
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

    /** Tạo bộ thẻ riêng. */
    @PostMapping("/decks")
    public ApiResponse<DeckResponse> createDeck(@Valid @RequestBody DeckRequest request,
                                                @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã tạo bộ thẻ", srsService.createDeck(me.getUsername(), request));
    }

    @GetMapping("/decks/{deckId}")
    public ApiResponse<DeckDetailResponse> deck(@PathVariable Integer deckId,
                                                @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(srsService.getDeck(deckId, me.getUsername()));
    }

    @PutMapping("/decks/{deckId}")
    public ApiResponse<DeckResponse> updateDeck(@PathVariable Integer deckId,
                                                @Valid @RequestBody DeckRequest request,
                                                @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã lưu bộ thẻ", srsService.updateDeck(deckId, me.getUsername(), request));
    }

    @DeleteMapping("/decks/{deckId}")
    public ApiResponse<Void> deleteDeck(@PathVariable Integer deckId,
                                        @AuthenticationPrincipal UserDetails me) {
        srsService.deleteDeck(deckId, me.getUsername());
        return ApiResponse.success("Đã xoá bộ thẻ", null);
    }

    /** Thêm thẻ tự soạn. */
    @PostMapping("/decks/{deckId}/cards")
    public ApiResponse<DeckCardView> addCard(@PathVariable Integer deckId,
                                             @Valid @RequestBody CustomCardRequest request,
                                             @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã thêm thẻ", srsService.addCustomCard(deckId, me.getUsername(), request));
    }

    @PutMapping("/decks/{deckId}/cards/{cardId}")
    public ApiResponse<DeckCardView> updateCard(@PathVariable Integer deckId,
                                                @PathVariable Integer cardId,
                                                @Valid @RequestBody CustomCardRequest request,
                                                @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã sửa thẻ",
                srsService.updateCustomCard(deckId, cardId, me.getUsername(), request));
    }

    /** Thêm từ vựng / chữ Hán có sẵn vào bộ. */
    @PostMapping("/decks/{deckId}/items")
    public ApiResponse<DeckCardView> addItem(@PathVariable Integer deckId,
                                             @Valid @RequestBody DeckItemRequest request,
                                             @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã thêm vào bộ", srsService.addExistingItem(deckId, me.getUsername(), request));
    }

    @DeleteMapping("/decks/{deckId}/items/{itemType}/{itemId}")
    public ApiResponse<Void> removeItem(@PathVariable Integer deckId,
                                        @PathVariable StudyItemType itemType,
                                        @PathVariable Integer itemId,
                                        @AuthenticationPrincipal UserDetails me) {
        srsService.removeCard(deckId, itemType, itemId, me.getUsername());
        return ApiResponse.success("Đã gỡ thẻ khỏi bộ", null);
    }

    /** Thêm bộ vào lịch học. Gọi lại để đồng bộ thẻ mới của bộ. */
    @PostMapping("/decks/{deckId}/enroll")
    public ApiResponse<Integer> enrollDeck(@PathVariable Integer deckId,
                                           @AuthenticationPrincipal UserDetails me) {
        int added = srsService.enrollDeck(deckId, me.getUsername());
        return ApiResponse.success(
                added == 0 ? "Bộ thẻ đã có trong lịch học" : "Đã thêm " + added + " thẻ vào lịch học",
                added);
    }

    /** Bỏ bộ khỏi lịch học. */
    @DeleteMapping("/decks/{deckId}/enroll")
    public ApiResponse<Integer> unenrollDeck(@PathVariable Integer deckId,
                                             @AuthenticationPrincipal UserDetails me) {
        int removed = srsService.unenrollDeck(deckId, me.getUsername());
        return ApiResponse.success("Đã bỏ bộ khỏi lịch học", removed);
    }

    /** Tìm từ vựng / chữ Hán có sẵn. */
    @GetMapping("/study/search")
    public ApiResponse<List<StudyContentHit>> searchContent(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) StudyItemType type,
            @RequestParam(required = false) Integer deckId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(srsService.searchContent(me.getUsername(), q, type, deckId));
    }

    /** Hàng đợi hôm nay; deckId để học riêng một bộ, extraNew để học thêm thẻ mới. */
    @GetMapping("/reviews/due")
    public ApiResponse<ReviewQueueResponse> dueCards(
            @RequestParam(required = false) Integer deckId,
            @RequestParam(required = false) Integer extraNew,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(srsService.getDueQueue(me.getUsername(), deckId, extraNew));
    }

    /** Người học vừa lật thẻ và tự đánh giá mình nhớ tới đâu. */
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
