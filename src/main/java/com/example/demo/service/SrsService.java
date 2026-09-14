package com.example.demo.service;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.dto.request.CustomCardRequest;
import com.example.demo.dto.request.DeckItemRequest;
import com.example.demo.dto.request.DeckRequest;
import com.example.demo.dto.request.ReviewGradeRequest;
import com.example.demo.dto.response.DeckCardView;
import com.example.demo.dto.response.DeckDetailResponse;
import com.example.demo.dto.response.DeckResponse;
import com.example.demo.dto.response.ReviewQueueResponse;
import com.example.demo.dto.response.ReviewResultResponse;
import com.example.demo.dto.response.StudyContentHit;

import java.util.List;
import java.util.Map;

/** Thẻ ghi nhớ: bộ thẻ, thẻ tự soạn và lịch lặp lại ngắt quãng. */
public interface SrsService {

    // ── Bộ thẻ ──────────────────────────────────────────────────────────────

    /** Bộ của mình trước, rồi bộ hệ thống; kèm tiến độ. */
    List<DeckResponse> listDecks(String studentEmail);

    DeckDetailResponse getDeck(Integer deckId, String studentEmail);

    /** Tạo bộ riêng; bộ tự vào lịch học. */
    DeckResponse createDeck(String studentEmail, DeckRequest request);

    DeckResponse updateDeck(Integer deckId, String studentEmail, DeckRequest request);

    void deleteDeck(Integer deckId, String studentEmail);

    DeckCardView addCustomCard(Integer deckId, String studentEmail, CustomCardRequest request);

    DeckCardView updateCustomCard(Integer deckId, Integer cardId, String studentEmail, CustomCardRequest request);

    DeckCardView addExistingItem(Integer deckId, String studentEmail, DeckItemRequest request);

    void removeCard(Integer deckId, StudyItemType itemType, Integer itemId, String studentEmail);

    /** Tìm từ vựng / chữ Hán có sẵn để thêm vào bộ. */
    List<StudyContentHit> searchContent(String studentEmail, String query, StudyItemType type, Integer deckId);

    // ── Lịch học ────────────────────────────────────────────────────────────

    /** Thêm bộ vào lịch; trả về số thẻ mới được thêm. */
    int enrollDeck(Integer deckId, String studentEmail);

    /** Bỏ bộ khỏi lịch; trả về số thẻ bị xoá khỏi lịch. */
    int unenrollDeck(Integer deckId, String studentEmail);

    /** Hàng đợi hôm nay, của một bộ hoặc mọi bộ. */
    ReviewQueueResponse getDueQueue(String studentEmail, Integer deckId, Integer extraNew);

    ReviewResultResponse review(StudyItemType itemType, Integer itemId,
                                ReviewGradeRequest request, String studentEmail);

    /** Số thẻ cần học hôm nay của một người. */
    long cardsAvailableToday(String userId);

    /** Như trên cho mọi người có thẻ cần học. */
    Map<String, Long> cardsAvailableTodayByUser();
}
