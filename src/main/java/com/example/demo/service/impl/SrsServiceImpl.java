package com.example.demo.service.impl;

import com.example.demo.domain.enums.ReviewGrade;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.domain.model.Deck;
import com.example.demo.domain.model.DeckItem;
import com.example.demo.domain.model.KanjiItem;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.UserCardState;
import com.example.demo.domain.model.VocabItem;
import com.example.demo.dto.request.ReviewGradeRequest;
import com.example.demo.dto.response.DeckResponse;
import com.example.demo.dto.response.ReviewCardResponse;
import com.example.demo.dto.response.ReviewQueueResponse;
import com.example.demo.dto.response.ReviewResultResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.DeckItemRepository;
import com.example.demo.repository.DeckRepository;
import com.example.demo.repository.KanjiItemRepository;
import com.example.demo.repository.UserCardStateRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VocabItemRepository;
import com.example.demo.service.SrsService;
import com.example.demo.service.srs.Sm2Scheduler;
import com.example.demo.util.DbTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cài đặt việc học thuộc bằng lặp lại ngắt quãng.
 *
 * Hai điểm đáng chú ý về mặt kỹ thuật:
 *
 * 1. Thẻ là tham chiếu đa hình. {@code UserCardStates} chỉ giữ (loại, id) chứ
 *    không có khoá ngoại tới hai bảng nội dung, nên mọi chỗ dựng dữ liệu hiển
 *    thị đều phải gom id theo loại rồi nạp một lượt. Xem {@link #loadCards}.
 *
 * 2. Hạn mức thẻ mỗi ngày là tính năng, không phải giới hạn kỹ thuật. Người
 *    nghỉ hai tuần quay lại sẽ có 400 thẻ quá hạn; ném cả 400 thẻ vào mặt họ
 *    là cách chắc chắn nhất để họ bỏ hẳn.
 */
@Service
@RequiredArgsConstructor
public class SrsServiceImpl implements SrsService {

    private static final Logger log = LoggerFactory.getLogger(SrsServiceImpl.class);

    private final DeckRepository deckRepository;
    private final DeckItemRepository deckItemRepository;
    private final UserCardStateRepository cardStateRepository;
    private final VocabItemRepository vocabRepository;
    private final KanjiItemRepository kanjiRepository;
    private final UserRepository userRepository;

    /** Số thẻ tối đa cho một phiên ôn. Xem chú thích ở đầu lớp. */
    @Value("${study.srs.daily-limit:100}")
    private int dailyLimit;

    /** Trần số thẻ được nạp vào một bộ trong một lần ghi danh. */
    @Value("${study.srs.max-enroll-per-deck:500}")
    private int maxEnrollPerDeck;

    // ── Bộ thẻ ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DeckResponse> listDecks(String studentEmail) {
        User student = requireStudent(studentEmail);
        List<Deck> decks = deckRepository.findVisibleTo(student.getUserId());
        if (decks.isEmpty()) {
            return List.of();
        }

        List<Integer> deckIds = decks.stream().map(Deck::getDeckId).toList();

        // Hai truy vấn gộp cho toàn bộ danh sách thay vì hai truy vấn cho mỗi
        // bộ — màn hình này hiện 5-20 bộ cùng lúc.
        Map<Integer, Long> totals = toCountMap(deckItemRepository.countByDeckIds(deckIds));
        Map<Integer, long[]> progress = toProgressMap(cardStateRepository.findDeckProgress(
                student.getUserId(), deckIds, Sm2Scheduler.MATURE_INTERVAL_DAYS));

        return decks.stream().map(deck -> {
            long[] mine = progress.getOrDefault(deck.getDeckId(), new long[]{0L, 0L});
            SubjectLevel level = deck.getLevel();
            return DeckResponse.builder()
                    .deckId(deck.getDeckId())
                    .name(deck.getName())
                    .description(deck.getDescription())
                    .levelId(level != null ? level.getLevelId() : null)
                    .levelName(level != null ? level.getLevelName() : null)
                    .systemDeck(deck.isSystemDeck())
                    .totalCards(totals.getOrDefault(deck.getDeckId(), 0L))
                    .enrolledCards(mine[0])
                    .masteredCards(mine[1])
                    .enrolled(mine[0] > 0)
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public int enrollDeck(Integer deckId, String studentEmail) {
        User student = requireStudent(studentEmail);
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ thẻ"));

        // Bộ riêng của người khác thì không được học. Bộ hệ thống và bộ công
        // khai thì thoải mái.
        boolean visible = deck.isSystemDeck()
                || Boolean.TRUE.equals(deck.getIsPublic())
                || (deck.getOwner() != null
                    && deck.getOwner().getUserId().equals(student.getUserId()));
        if (!visible) {
            throw new UnauthorizedException("Bộ thẻ này không được chia sẻ");
        }

        List<DeckItem> items = deckItemRepository.findByDeck_DeckIdOrderByOrderNoAsc(deckId);
        if (items.isEmpty()) {
            throw new BusinessException("Bộ thẻ này chưa có thẻ nào");
        }
        if (items.size() > maxEnrollPerDeck) {
            items = items.subList(0, maxEnrollPerDeck);
        }

        // Gom id theo loại rồi hỏi một lần cho mỗi loại: thẻ nào đã học rồi thì
        // bỏ qua để không đặt lại tiến độ (xem Javadoc của SrsService.enrollDeck).
        Map<StudyItemType, List<Integer>> wanted = items.stream()
                .collect(Collectors.groupingBy(DeckItem::itemType,
                        () -> new EnumMap<>(StudyItemType.class),
                        Collectors.mapping(DeckItem::itemId, Collectors.toList())));

        Map<StudyItemType, Set<Integer>> already = new EnumMap<>(StudyItemType.class);
        for (Map.Entry<StudyItemType, List<Integer>> e : wanted.entrySet()) {
            Set<Integer> enrolled = cardStateRepository
                    .findEnrolled(student.getUserId(), e.getKey(), e.getValue())
                    .stream()
                    .map(UserCardState::getItemId)
                    .collect(Collectors.toSet());
            already.put(e.getKey(), enrolled);
        }

        LocalDateTime now = DbTime.now();
        List<UserCardState> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (DeckItem item : items) {
            StudyItemType type = item.itemType();
            Integer itemId = item.itemId();
            if (already.getOrDefault(type, Set.of()).contains(itemId)) {
                continue;
            }
            // Một bộ về lý thuyết không lặp thẻ (khoá chính chặn), nhưng chốt
            // thêm ở đây để một lần ghi danh không bao giờ đụng UNIQUE.
            if (!seen.add(type + ":" + itemId)) {
                continue;
            }
            fresh.add(UserCardState.builder()
                    .user(student)
                    .itemType(type)
                    .itemId(itemId)
                    // Thẻ mới đến hạn ngay: ghi danh xong là học được luôn,
                    // không phải đợi sang ngày hôm sau.
                    .dueAt(now)
                    .build());
        }

        if (!fresh.isEmpty()) {
            cardStateRepository.saveAll(fresh);
        }
        log.info("Ghi danh bộ thẻ deckId={} userId={} thẻ mới={}",
                deckId, student.getUserId(), fresh.size());
        return fresh.size();
    }

    // ── Phiên ôn tập ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReviewQueueResponse getDueQueue(String studentEmail, Integer limit) {
        User student = requireStudent(studentEmail);
        LocalDateTime now = DbTime.now();

        int size = limit == null ? dailyLimit : Math.min(Math.max(limit, 1), dailyLimit);
        List<UserCardState> due = cardStateRepository.findDue(
                student.getUserId(), now, PageRequest.of(0, size));

        List<ReviewCardResponse> cards = loadCards(due);

        return ReviewQueueResponse.builder()
                .cards(cards)
                .dueCount(cardStateRepository.countDue(student.getUserId(), now))
                .newCount(due.stream().filter(UserCardState::isNew).count())
                .totalCards(cardStateRepository.countByUser_UserId(student.getUserId()))
                .matureCards(cardStateRepository.countMature(
                        student.getUserId(), Sm2Scheduler.MATURE_INTERVAL_DAYS))
                .dailyLimit(dailyLimit)
                .build();
    }

    @Override
    @Transactional
    public ReviewResultResponse review(StudyItemType itemType, Integer itemId,
                                       ReviewGradeRequest request, String studentEmail) {
        User student = requireStudent(studentEmail);
        ReviewGrade grade = request == null ? null : request.getGrade();
        if (grade == null) {
            throw new BusinessException("Chưa chọn mức độ nhớ");
        }

        // Tra theo (người, loại, id): không có đường nào chạm tới thẻ của
        // người khác, kể cả khi đoán đúng CardStateID.
        UserCardState state = cardStateRepository
                .findByUser_UserIdAndItemTypeAndItemId(student.getUserId(), itemType, itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Thẻ này chưa nằm trong danh sách học của bạn"));

        LocalDateTime now = DbTime.now();
        state.applyReview(grade, now);
        cardStateRepository.save(state);

        return ReviewResultResponse.builder()
                .itemId(itemId)
                .intervalDays(state.getIntervalDays())
                .dueAt(state.getDueAt())
                .easeFactor(state.getEaseFactor())
                .repetitions(state.getRepetitions())
                .mature(state.isMature())
                // Đếm lại sau khi đã lưu: thẻ vừa trả lời "Quên" có khoảng cách
                // 0 ngày nên vẫn nằm trong số đến hạn, và người học cần thấy
                // đúng điều đó thay vì tưởng mình đã xong.
                .remainingDue(cardStateRepository.countDue(student.getUserId(), now))
                .build();
    }

    // ── Hỗ trợ ──────────────────────────────────────────────────────────────

    /**
     * Dựng dữ liệu hiển thị cho một loạt thẻ.
     *
     * Gom id theo loại rồi nạp mỗi loại một truy vấn — tối đa hai truy vấn cho
     * cả phiên 100 thẻ, thay vì 100 lượt tra bảng.
     */
    private List<ReviewCardResponse> loadCards(List<UserCardState> states) {
        if (states.isEmpty()) {
            return List.of();
        }

        List<Integer> vocabIds = states.stream()
                .filter(s -> s.getItemType() == StudyItemType.VOCAB)
                .map(UserCardState::getItemId).toList();
        List<Integer> kanjiIds = states.stream()
                .filter(s -> s.getItemType() == StudyItemType.KANJI)
                .map(UserCardState::getItemId).toList();

        Map<Integer, VocabItem> vocab = vocabIds.isEmpty() ? Map.of()
                : vocabRepository.findByVocabIdIn(vocabIds).stream()
                        .collect(Collectors.toMap(VocabItem::getVocabId, v -> v));
        Map<Integer, KanjiItem> kanji = kanjiIds.isEmpty() ? Map.of()
                : kanjiRepository.findByKanjiIdIn(kanjiIds).stream()
                        .collect(Collectors.toMap(KanjiItem::getKanjiId, k -> k));

        List<ReviewCardResponse> cards = new ArrayList<>(states.size());
        for (UserCardState state : states) {
            ReviewCardResponse card = state.getItemType() == StudyItemType.VOCAB
                    ? toCard(state, vocab.get(state.getItemId()))
                    : toCard(state, kanji.get(state.getItemId()));
            // Thẻ trỏ tới nội dung đã bị xoá: bỏ qua thay vì ném lỗi cả phiên.
            // Tham chiếu đa hình không có khoá ngoại nên tình huống này là có thật.
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    private ReviewCardResponse toCard(UserCardState state, VocabItem item) {
        if (item == null) {
            return null;
        }
        return baseCard(state)
                .prompt(item.getWord())
                .reading(item.getReading())
                .meaning(item.getMeaning())
                .partOfSpeech(item.getPartOfSpeech())
                .exampleSentence(item.getExampleSentence())
                .exampleMeaning(item.getExampleMeaning())
                .audioUrl(item.getAudioUrl())
                .build();
    }

    private ReviewCardResponse toCard(UserCardState state, KanjiItem item) {
        if (item == null) {
            return null;
        }
        return baseCard(state)
                .prompt(item.getGlyph())
                .meaning(item.getMeaning())
                .onyomi(item.getOnyomi())
                .kunyomi(item.getKunyomi())
                .strokeCount(item.getStrokeCount())
                .radical(item.getRadical())
                .mnemonic(item.getMnemonic())
                .build();
    }

    /** Phần chung của mọi thẻ: định danh và trạng thái ôn tập. */
    private ReviewCardResponse.ReviewCardResponseBuilder baseCard(UserCardState state) {
        return ReviewCardResponse.builder()
                .itemType(state.getItemType())
                .itemId(state.getItemId())
                .isNew(state.isNew())
                .repetitions(state.getRepetitions())
                .intervalDays(state.getIntervalDays())
                .dueAt(state.getDueAt());
    }

    /** [deckId, count] -> map. */
    private Map<Integer, Long> toCountMap(List<Object[]> rows) {
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Integer) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /** [deckId, đã ghi danh, đã thuộc] -> map deckId -> {đã ghi danh, đã thuộc}. */
    private Map<Integer, long[]> toProgressMap(List<Object[]> rows) {
        Map<Integer, long[]> map = new HashMap<>();
        for (Object[] row : rows) {
            long enrolled = row[1] == null ? 0L : ((Number) row[1]).longValue();
            // SUM(CASE...) trả null khi nhóm rỗng — không có dòng nào thoả thì
            // Hibernate cho null chứ không cho 0.
            long mature = row[2] == null ? 0L : ((Number) row[2]).longValue();
            map.put((Integer) row[0], new long[]{enrolled, mature});
        }
        return map;
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ thí sinh mới có lịch học thẻ");
        }
        return user;
    }
}
