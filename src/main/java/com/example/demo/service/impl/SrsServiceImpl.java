package com.example.demo.service.impl;

import com.example.demo.domain.enums.ReviewGrade;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.domain.model.CustomCard;
import com.example.demo.domain.model.Deck;
import com.example.demo.domain.model.DeckEnrollment;
import com.example.demo.domain.model.DeckItem;
import com.example.demo.domain.model.DeckItemKey;
import com.example.demo.domain.model.KanjiItem;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.UserCardState;
import com.example.demo.domain.model.VocabItem;
import com.example.demo.dto.request.CustomCardRequest;
import com.example.demo.dto.request.DeckItemRequest;
import com.example.demo.dto.request.DeckRequest;
import com.example.demo.dto.request.ReviewGradeRequest;
import com.example.demo.dto.response.DeckCardView;
import com.example.demo.dto.response.DeckDetailResponse;
import com.example.demo.dto.response.DeckResponse;
import com.example.demo.dto.response.ReviewCardResponse;
import com.example.demo.dto.response.ReviewQueueResponse;
import com.example.demo.dto.response.ReviewResultResponse;
import com.example.demo.dto.response.StudyContentHit;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.CustomCardRepository;
import com.example.demo.repository.DeckEnrollmentRepository;
import com.example.demo.repository.DeckItemRepository;
import com.example.demo.repository.DeckRepository;
import com.example.demo.repository.KanjiItemRepository;
import com.example.demo.repository.SubjectLevelRepository;
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
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Thẻ ghi nhớ: bộ thẻ, thẻ tự soạn và lịch SM-2. */
@Service
@RequiredArgsConstructor
public class SrsServiceImpl implements SrsService {

    private static final Logger log = LoggerFactory.getLogger(SrsServiceImpl.class);

    private static final int SEARCH_LIMIT = 20;
    private static final int MAX_EXTRA_NEW = 50;

    private final DeckRepository deckRepository;
    private final DeckItemRepository deckItemRepository;
    private final DeckEnrollmentRepository enrollmentRepository;
    private final UserCardStateRepository cardStateRepository;
    private final VocabItemRepository vocabRepository;
    private final KanjiItemRepository kanjiRepository;
    private final CustomCardRepository customCardRepository;
    private final SubjectLevelRepository levelRepository;
    private final UserRepository userRepository;

    /** Trần thẻ ôn trong một phiên. */
    @Value("${study.srs.daily-limit:100}")
    private int dailyLimit;

    /** Số thẻ mới được mở mỗi ngày. */
    @Value("${study.srs.new-per-day:20}")
    private int newPerDay;

    /** Trần số thẻ một bộ. */
    @Value("${study.srs.max-enroll-per-deck:500}")
    private int maxCardsPerDeck;

    /** Nội dung hiển thị chung của một thẻ. */
    private record Content(String front, String reading, String back, String example,
                           String exampleMeaning, String note, Object source) {
    }

    private record ItemRef(StudyItemType type, Integer id) {
    }

    // ── Bộ thẻ ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DeckResponse> listDecks(String studentEmail) {
        User student = requireStudent(studentEmail);
        List<DeckResponse> all = toDeckResponses(deckRepository.findVisibleTo(student.getUserId()), student);
        List<DeckResponse> mine = new ArrayList<>(all.stream().filter(DeckResponse::isMine).toList());
        mine.sort((a, b) -> b.getDeckId() - a.getDeckId());
        mine.addAll(all.stream().filter(d -> !d.isMine()).toList());
        return mine;
    }

    @Override
    @Transactional(readOnly = true)
    public DeckDetailResponse getDeck(Integer deckId, String studentEmail) {
        User student = requireStudent(studentEmail);
        Deck deck = requireVisibleDeck(deckId, student);
        boolean mine = isOwner(deck, student);
        LocalDateTime now = DbTime.now();

        List<DeckItem> items = deckItemRepository.findByDeck_DeckIdOrderByOrderNoAsc(deckId);
        Map<String, UserCardState> states = cardStateRepository.findInDeck(student.getUserId(), deckId).stream()
                .collect(Collectors.toMap(s -> key(s.getItemType(), s.getItemId()), s -> s, (a, b) -> a));
        Map<String, Content> contents = loadContents(items.stream()
                .map(i -> new ItemRef(i.itemType(), i.itemId())).toList());

        List<DeckCardView> cards = new ArrayList<>(items.size());
        for (DeckItem item : items) {
            String k = key(item.itemType(), item.itemId());
            Content content = contents.get(k);
            if (content != null) {
                cards.add(toCardView(item, content, states.get(k), mine, now));
            }
        }
        return DeckDetailResponse.builder()
                .deck(toDeckResponses(List.of(deck), student).get(0))
                .cards(cards)
                .build();
    }

    @Override
    @Transactional
    public DeckResponse createDeck(String studentEmail, DeckRequest request) {
        User student = requireStudent(studentEmail);
        Deck deck = deckRepository.save(Deck.builder()
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .level(request.getLevelId() == null ? null : requireLevel(request.getLevelId()))
                .owner(student)
                .isPublic(false)
                .build());
        enrollmentRepository.save(DeckEnrollment.builder()
                .userId(student.getUserId())
                .deckId(deck.getDeckId())
                .enrolledAt(DbTime.now())
                .build());
        log.info("Tạo bộ thẻ deckId={} userId={}", deck.getDeckId(), student.getUserId());
        return toDeckResponses(List.of(deck), student).get(0);
    }

    @Override
    @Transactional
    public DeckResponse updateDeck(Integer deckId, String studentEmail, DeckRequest request) {
        User student = requireStudent(studentEmail);
        Deck deck = requireOwnDeck(deckId, student);
        deck.setName(request.getName().trim());
        deck.setDescription(trimToNull(request.getDescription()));
        deck.setLevel(request.getLevelId() == null ? null : requireLevel(request.getLevelId()));
        deckRepository.save(deck);
        return toDeckResponses(List.of(deck), student).get(0);
    }

    @Override
    @Transactional
    public void deleteDeck(Integer deckId, String studentEmail) {
        User student = requireStudent(studentEmail);
        Deck deck = requireOwnDeck(deckId, student);
        if (deckRepository.usedByLesson(deckId)) {
            throw new BusinessException("Bộ thẻ đang được dùng trong lộ trình ôn tập, không xoá được");
        }
        for (DeckItem item : deckItemRepository.findByDeck_DeckIdOrderByOrderNoAsc(deckId)) {
            dropItemFromSchedule(deckId, student, item.itemType(), item.itemId());
        }
        deckRepository.deleteDeckById(deck.getDeckId());
        log.info("Xoá bộ thẻ deckId={} userId={}", deckId, student.getUserId());
    }

    @Override
    @Transactional
    public DeckCardView addCustomCard(Integer deckId, String studentEmail, CustomCardRequest request) {
        User student = requireStudent(studentEmail);
        Deck deck = requireOwnDeck(deckId, student);
        requireRoomInDeck(deckId);

        CustomCard card = customCardRepository.save(CustomCard.builder()
                .owner(student)
                .front(request.getFront().trim())
                .reading(trimToNull(request.getReading()))
                .back(request.getBack().trim())
                .example(trimToNull(request.getExample()))
                .exampleMeaning(trimToNull(request.getExampleMeaning()))
                .note(trimToNull(request.getNote()))
                .build());
        DeckItem item = addDeckItem(deck, StudyItemType.CUSTOM, card.getCardId());
        scheduleForEnrolled(deckId, StudyItemType.CUSTOM, card.getCardId());
        return viewOf(item, student, true);
    }

    @Override
    @Transactional
    public DeckCardView updateCustomCard(Integer deckId, Integer cardId, String studentEmail,
                                         CustomCardRequest request) {
        User student = requireStudent(studentEmail);
        requireOwnDeck(deckId, student);
        DeckItem item = deckItemRepository.findById(new DeckItemKey(deckId, StudyItemType.CUSTOM, cardId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ trong bộ"));
        CustomCard card = customCardRepository.findById(cardId)
                .filter(c -> c.getOwner().getUserId().equals(student.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ"));

        card.setFront(request.getFront().trim());
        card.setReading(trimToNull(request.getReading()));
        card.setBack(request.getBack().trim());
        card.setExample(trimToNull(request.getExample()));
        card.setExampleMeaning(trimToNull(request.getExampleMeaning()));
        card.setNote(trimToNull(request.getNote()));
        customCardRepository.save(card);
        return viewOf(item, student, true);
    }

    @Override
    @Transactional
    public DeckCardView addExistingItem(Integer deckId, String studentEmail, DeckItemRequest request) {
        User student = requireStudent(studentEmail);
        Deck deck = requireOwnDeck(deckId, student);
        StudyItemType type = request.getItemType();
        Integer itemId = request.getItemId();

        boolean exists = switch (type) {
            case VOCAB -> vocabRepository.existsById(itemId);
            case KANJI -> kanjiRepository.existsById(itemId);
            case CUSTOM -> throw new BusinessException("Thẻ tự soạn thì dùng form Thêm thẻ");
        };
        if (!exists) {
            throw new ResourceNotFoundException("Không tìm thấy từ cần thêm");
        }
        if (deckItemRepository.existsById(new DeckItemKey(deckId, type, itemId))) {
            throw new BusinessException("Thẻ này đã có trong bộ");
        }
        requireRoomInDeck(deckId);

        DeckItem item = addDeckItem(deck, type, itemId);
        scheduleForEnrolled(deckId, type, itemId);
        return viewOf(item, student, true);
    }

    @Override
    @Transactional
    public void removeCard(Integer deckId, StudyItemType itemType, Integer itemId, String studentEmail) {
        User student = requireStudent(studentEmail);
        requireOwnDeck(deckId, student);
        DeckItemKey key = new DeckItemKey(deckId, itemType, itemId);
        if (!deckItemRepository.existsById(key)) {
            throw new ResourceNotFoundException("Không tìm thấy thẻ trong bộ");
        }
        dropItemFromSchedule(deckId, student, itemType, itemId);
        if (itemType != StudyItemType.CUSTOM) {
            deckItemRepository.deleteById(key);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyContentHit> searchContent(String studentEmail, String query, StudyItemType type,
                                               Integer deckId) {
        User student = requireStudent(studentEmail);
        String term = query == null ? "" : query.trim().toLowerCase().replace("%", "").replace("_", "");
        if (term.isEmpty()) {
            return List.of();
        }
        if (deckId != null) {
            requireVisibleDeck(deckId, student);
        }
        String like = "%" + term + "%";
        PageRequest page = PageRequest.of(0, SEARCH_LIMIT);

        List<StudyContentHit> hits = new ArrayList<>();
        if (type == null || type == StudyItemType.VOCAB) {
            for (VocabItem v : vocabRepository.search(like, page)) {
                hits.add(StudyContentHit.builder()
                        .itemType(StudyItemType.VOCAB).itemId(v.getVocabId())
                        .front(v.getWord()).reading(v.getReading()).meaning(v.getMeaning())
                        .levelName(v.getLevel() == null ? null : v.getLevel().getLevelName())
                        .build());
            }
        }
        if (type == null || type == StudyItemType.KANJI) {
            for (KanjiItem k : kanjiRepository.search(like, page)) {
                hits.add(StudyContentHit.builder()
                        .itemType(StudyItemType.KANJI).itemId(k.getKanjiId())
                        .front(k.getGlyph()).reading(joinReadings(k)).meaning(k.getMeaning())
                        .levelName(k.getLevel() == null ? null : k.getLevel().getLevelName())
                        .build());
            }
        }
        List<StudyContentHit> limited = hits.size() > SEARCH_LIMIT ? hits.subList(0, SEARCH_LIMIT) : hits;

        if (deckId != null && !limited.isEmpty()) {
            for (StudyItemType t : List.of(StudyItemType.VOCAB, StudyItemType.KANJI)) {
                List<Integer> ids = limited.stream().filter(h -> h.getItemType() == t)
                        .map(StudyContentHit::getItemId).toList();
                if (ids.isEmpty()) {
                    continue;
                }
                Set<Integer> inDeck = new HashSet<>(deckItemRepository.findItemIdsInDeck(deckId, t, ids));
                limited.stream().filter(h -> h.getItemType() == t && inDeck.contains(h.getItemId()))
                        .forEach(h -> h.setInDeck(true));
            }
        }
        return limited;
    }

    // ── Lịch học ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public int enrollDeck(Integer deckId, String studentEmail) {
        User student = requireStudent(studentEmail);
        Deck deck = requireVisibleDeck(deckId, student);
        List<DeckItem> items = deckItemRepository.findByDeck_DeckIdOrderByOrderNoAsc(deckId);
        if (items.isEmpty() && !isOwner(deck, student)) {
            throw new BusinessException("Bộ thẻ này chưa có thẻ nào");
        }
        if (!enrollmentRepository.existsByUserIdAndDeckId(student.getUserId(), deckId)) {
            enrollmentRepository.save(DeckEnrollment.builder()
                    .userId(student.getUserId())
                    .deckId(deckId)
                    .enrolledAt(DbTime.now())
                    .build());
        }
        int added = createMissingStates(student, items);
        log.info("Thêm bộ vào lịch deckId={} userId={} thẻ mới={}", deckId, student.getUserId(), added);
        return added;
    }

    @Override
    @Transactional
    public int unenrollDeck(Integer deckId, String studentEmail) {
        User student = requireStudent(studentEmail);
        requireVisibleDeck(deckId, student);
        Optional<DeckEnrollment> enrollment =
                enrollmentRepository.findById(new DeckEnrollment.Key(student.getUserId(), deckId));
        if (enrollment.isEmpty()) {
            return 0;
        }
        enrollmentRepository.delete(enrollment.get());
        enrollmentRepository.flush();

        int removed = 0;
        for (DeckItem item : deckItemRepository.findByDeck_DeckIdOrderByOrderNoAsc(deckId)) {
            if (!enrollmentRepository.itemInOtherEnrolledDeck(
                    student.getUserId(), deckId, item.itemType(), item.itemId())) {
                removed += cardStateRepository.deleteForUser(student.getUserId(), item.itemType(), item.itemId());
            }
        }
        log.info("Bỏ bộ khỏi lịch deckId={} userId={} thẻ xoá={}", deckId, student.getUserId(), removed);
        return removed;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewQueueResponse getDueQueue(String studentEmail, Integer deckId, Integer extraNew) {
        User student = requireStudent(studentEmail);
        String userId = student.getUserId();
        Deck deck = deckId == null ? null : requireVisibleDeck(deckId, student);
        LocalDateTime now = DbTime.now();
        int extra = extraNew == null ? 0 : Math.min(Math.max(extraNew, 0), MAX_EXTRA_NEW);

        long reviewDue = cardStateRepository.countDueReviews(userId, now, deckId);
        List<UserCardState> reviews = cardStateRepository.findDueReviews(
                userId, now, deckId, PageRequest.of(0, dailyLimit));

        long studiedToday = cardStateRepository.countStudiedSince(userId, startOfDay(now));
        int todayLeft = (int) Math.max(0, newPerDay - studiedToday);
        long newWaiting = cardStateRepository.countNewCards(userId, deckId);
        int allowance = todayLeft + extra;
        List<UserCardState> fresh = allowance == 0 ? List.of()
                : cardStateRepository.findNewCards(userId, deckId, PageRequest.of(0, allowance));

        List<ReviewCardResponse> cards = new ArrayList<>(loadCards(reviews));
        cards.addAll(loadCards(fresh));
        long newAvailable = Math.min(newWaiting, todayLeft);

        return ReviewQueueResponse.builder()
                .deckId(deckId)
                .deckName(deck == null ? null : deck.getName())
                .cards(cards)
                .reviewDue(reviewDue)
                .newAvailable(newAvailable)
                .newWaiting(newWaiting)
                .newStudiedToday(studiedToday)
                .newPerDay(newPerDay)
                .dueCount(reviewDue + newAvailable)
                .newCount(fresh.size())
                .totalCards(cardStateRepository.countByUser_UserId(userId))
                .matureCards(cardStateRepository.countMature(userId, Sm2Scheduler.MATURE_INTERVAL_DAYS))
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
        UserCardState state = cardStateRepository
                .findByUser_UserIdAndItemTypeAndItemId(student.getUserId(), itemType, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Thẻ này chưa nằm trong lịch học của bạn"));

        LocalDateTime now = DbTime.now();
        state.applyReview(grade, now);
        cardStateRepository.saveAndFlush(state);

        return ReviewResultResponse.builder()
                .itemId(itemId)
                .intervalDays(state.getIntervalDays())
                .dueAt(state.getDueAt())
                .easeFactor(state.getEaseFactor())
                .repetitions(state.getRepetitions())
                .mature(state.isMature())
                .remainingDue(availableToday(student.getUserId(), now))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long cardsAvailableToday(String userId) {
        return availableToday(userId, DbTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> cardsAvailableTodayByUser() {
        LocalDateTime now = DbTime.now();
        Map<String, Long> reviews = toUserCountMap(cardStateRepository.countDueReviewsByUser(now));
        Map<String, Long> waiting = toUserCountMap(cardStateRepository.countNewByUser());
        Map<String, Long> studied = toUserCountMap(cardStateRepository.countStudiedSinceByUser(startOfDay(now)));

        Set<String> users = new HashSet<>(reviews.keySet());
        users.addAll(waiting.keySet());
        Map<String, Long> out = new LinkedHashMap<>();
        for (String userId : users) {
            long newToday = Math.min(waiting.getOrDefault(userId, 0L),
                    Math.max(0, newPerDay - studied.getOrDefault(userId, 0L)));
            long total = reviews.getOrDefault(userId, 0L) + newToday;
            if (total > 0) {
                out.put(userId, total);
            }
        }
        return out;
    }

    // ── Hỗ trợ: lịch ────────────────────────────────────────────────────────

    private long availableToday(String userId, LocalDateTime now) {
        long reviewDue = cardStateRepository.countDueReviews(userId, now, null);
        long todayLeft = Math.max(0, newPerDay - cardStateRepository.countStudiedSince(userId, startOfDay(now)));
        return reviewDue + Math.min(cardStateRepository.countNewCards(userId, null), todayLeft);
    }

    /** Tạo trạng thái cho thẻ chưa có trong lịch, giữ thứ tự của bộ. */
    private int createMissingStates(User student, List<DeckItem> items) {
        if (items.isEmpty()) {
            return 0;
        }
        List<DeckItem> capped = items.size() > maxCardsPerDeck ? items.subList(0, maxCardsPerDeck) : items;
        Map<StudyItemType, List<Integer>> wanted = capped.stream()
                .collect(Collectors.groupingBy(DeckItem::itemType,
                        () -> new EnumMap<>(StudyItemType.class),
                        Collectors.mapping(DeckItem::itemId, Collectors.toList())));

        Set<String> already = new HashSet<>();
        for (Map.Entry<StudyItemType, List<Integer>> e : wanted.entrySet()) {
            cardStateRepository.findEnrolled(student.getUserId(), e.getKey(), e.getValue())
                    .forEach(s -> already.add(key(s.getItemType(), s.getItemId())));
        }

        LocalDateTime now = DbTime.now();
        List<UserCardState> fresh = new ArrayList<>();
        for (DeckItem item : capped) {
            if (already.add(key(item.itemType(), item.itemId()))) {
                fresh.add(newState(student, item.itemType(), item.itemId(), now));
            }
        }
        cardStateRepository.saveAll(fresh);
        return fresh.size();
    }

    /** Thẻ vừa thêm vào bộ thì vào lịch của người đã ghi danh bộ đó. */
    private void scheduleForEnrolled(Integer deckId, StudyItemType type, Integer itemId) {
        LocalDateTime now = DbTime.now();
        for (String userId : enrollmentRepository.findUserIdsByDeckId(deckId)) {
            if (cardStateRepository.findByUser_UserIdAndItemTypeAndItemId(userId, type, itemId).isEmpty()) {
                cardStateRepository.save(newState(userRepository.getReferenceById(userId), type, itemId, now));
            }
        }
    }

    /** Gỡ ảnh hưởng của một thẻ khi rời bộ: thẻ tự soạn bị xoá hẳn, thẻ có sẵn rời lịch nếu không còn ở bộ khác. */
    private void dropItemFromSchedule(Integer deckId, User student, StudyItemType type, Integer itemId) {
        if (type == StudyItemType.CUSTOM) {
            cardStateRepository.deleteForItem(type, itemId);
            deckItemRepository.deleteByItem(type, itemId);
            customCardRepository.deleteById(itemId);
            return;
        }
        if (!enrollmentRepository.itemInOtherEnrolledDeck(student.getUserId(), deckId, type, itemId)) {
            cardStateRepository.deleteForUser(student.getUserId(), type, itemId);
        }
    }

    private static UserCardState newState(User user, StudyItemType type, Integer itemId, LocalDateTime now) {
        return UserCardState.builder().user(user).itemType(type).itemId(itemId).dueAt(now).build();
    }

    private DeckItem addDeckItem(Deck deck, StudyItemType type, Integer itemId) {
        int order = deckItemRepository.findMaxOrderNo(deck.getDeckId()) + 1;
        return deckItemRepository.save(DeckItem.builder()
                .id(new DeckItemKey(deck.getDeckId(), type, itemId))
                .deck(deck)
                .orderNo(order)
                .build());
    }

    private void requireRoomInDeck(Integer deckId) {
        if (deckItemRepository.countByDeck_DeckId(deckId) >= maxCardsPerDeck) {
            throw new BusinessException("Một bộ thẻ tối đa " + maxCardsPerDeck + " thẻ");
        }
    }

    // ── Hỗ trợ: hiển thị ────────────────────────────────────────────────────

    private List<DeckResponse> toDeckResponses(List<Deck> decks, User student) {
        if (decks.isEmpty()) {
            return List.of();
        }
        String userId = student.getUserId();
        List<Integer> ids = decks.stream().map(Deck::getDeckId).toList();

        Map<Integer, Long> totals = new HashMap<>();
        for (Object[] row : deckItemRepository.countByDeckIds(ids)) {
            totals.put((Integer) row[0], num(row[1]));
        }
        Map<Integer, long[]> progress = new HashMap<>();
        for (Object[] row : cardStateRepository.findDeckProgress(
                userId, ids, Sm2Scheduler.MATURE_INTERVAL_DAYS, DbTime.now())) {
            progress.put((Integer) row[0], new long[]{num(row[1]), num(row[2]), num(row[3]), num(row[4])});
        }
        Set<Integer> enrolled = enrollmentRepository.findByUserId(userId).stream()
                .map(DeckEnrollment::getDeckId).collect(Collectors.toSet());

        return decks.stream().map(deck -> {
            long[] p = progress.getOrDefault(deck.getDeckId(), new long[4]);
            SubjectLevel level = deck.getLevel();
            return DeckResponse.builder()
                    .deckId(deck.getDeckId())
                    .name(deck.getName())
                    .description(deck.getDescription())
                    .levelId(level == null ? null : level.getLevelId())
                    .levelName(level == null ? null : level.getLevelName())
                    .systemDeck(deck.isSystemDeck())
                    .mine(isOwner(deck, student))
                    .totalCards(totals.getOrDefault(deck.getDeckId(), 0L))
                    .enrolled(enrolled.contains(deck.getDeckId()))
                    .enrolledCards(p[0])
                    .masteredCards(p[1])
                    .dueCards(p[2])
                    .newCards(p[3])
                    .build();
        }).toList();
    }

    private DeckCardView viewOf(DeckItem item, User student, boolean mine) {
        Content content = loadContents(List.of(new ItemRef(item.itemType(), item.itemId())))
                .get(key(item.itemType(), item.itemId()));
        UserCardState state = cardStateRepository
                .findByUser_UserIdAndItemTypeAndItemId(student.getUserId(), item.itemType(), item.itemId())
                .orElse(null);
        return toCardView(item, content, state, mine, DbTime.now());
    }

    private DeckCardView toCardView(DeckItem item, Content c, UserCardState state, boolean mine,
                                    LocalDateTime now) {
        String status = state == null ? "NOT_ENROLLED"
                : state.isNew() ? "NEW"
                : state.isMature() ? "MATURE" : "LEARNING";
        return DeckCardView.builder()
                .itemType(item.itemType())
                .itemId(item.itemId())
                .orderNo(item.getOrderNo())
                .front(c.front())
                .reading(c.reading())
                .back(c.back())
                .example(c.example())
                .exampleMeaning(c.exampleMeaning())
                .note(c.note())
                .status(status)
                .intervalDays(state == null ? null : state.getIntervalDays())
                .dueAt(state == null || state.isNew() ? null : state.getDueAt())
                .due(state != null && !state.isNew() && state.isDueAt(now))
                .lapses(state == null ? null : state.getLapses())
                .editable(mine && item.itemType() == StudyItemType.CUSTOM)
                .removable(mine)
                .build();
    }

    /** Nạp nội dung cho một loạt thẻ, mỗi loại một truy vấn. */
    private Map<String, Content> loadContents(Collection<ItemRef> refs) {
        Map<StudyItemType, List<Integer>> ids = refs.stream()
                .collect(Collectors.groupingBy(ItemRef::type, () -> new EnumMap<>(StudyItemType.class),
                        Collectors.mapping(ItemRef::id, Collectors.toList())));
        Map<String, Content> out = new HashMap<>();
        if (ids.containsKey(StudyItemType.VOCAB)) {
            for (VocabItem v : vocabRepository.findByVocabIdIn(ids.get(StudyItemType.VOCAB))) {
                out.put(key(StudyItemType.VOCAB, v.getVocabId()), new Content(v.getWord(), v.getReading(),
                        v.getMeaning(), v.getExampleSentence(), v.getExampleMeaning(), v.getPartOfSpeech(), v));
            }
        }
        if (ids.containsKey(StudyItemType.KANJI)) {
            for (KanjiItem k : kanjiRepository.findByKanjiIdIn(ids.get(StudyItemType.KANJI))) {
                out.put(key(StudyItemType.KANJI, k.getKanjiId()), new Content(k.getGlyph(), joinReadings(k),
                        k.getMeaning(), null, null, k.getMnemonic(), k));
            }
        }
        if (ids.containsKey(StudyItemType.CUSTOM)) {
            for (CustomCard c : customCardRepository.findByCardIdIn(ids.get(StudyItemType.CUSTOM))) {
                out.put(key(StudyItemType.CUSTOM, c.getCardId()), new Content(c.getFront(), c.getReading(),
                        c.getBack(), c.getExample(), c.getExampleMeaning(), c.getNote(), c));
            }
        }
        return out;
    }

    /** Dựng thẻ cho phiên ôn; thẻ mất nội dung thì bỏ qua. */
    private List<ReviewCardResponse> loadCards(List<UserCardState> states) {
        if (states.isEmpty()) {
            return List.of();
        }
        Map<String, Content> contents = loadContents(states.stream()
                .map(s -> new ItemRef(s.getItemType(), s.getItemId())).toList());

        List<ReviewCardResponse> cards = new ArrayList<>(states.size());
        for (UserCardState state : states) {
            Content c = contents.get(key(state.getItemType(), state.getItemId()));
            if (c == null) {
                continue;
            }
            ReviewCardResponse.ReviewCardResponseBuilder card = ReviewCardResponse.builder()
                    .itemType(state.getItemType())
                    .itemId(state.getItemId())
                    .isNew(state.isNew())
                    .repetitions(state.getRepetitions())
                    .intervalDays(state.getIntervalDays())
                    .dueAt(state.getDueAt())
                    .nextIntervals(Sm2Scheduler.preview(state.getEaseFactor(),
                            nz(state.getIntervalDays()), nz(state.getRepetitions()), nz(state.getLapses())))
                    .prompt(c.front())
                    .meaning(c.back());

            if (c.source() instanceof VocabItem v) {
                card.reading(v.getReading())
                        .partOfSpeech(v.getPartOfSpeech())
                        .exampleSentence(v.getExampleSentence())
                        .exampleMeaning(v.getExampleMeaning())
                        .audioUrl(v.getAudioUrl());
            } else if (c.source() instanceof KanjiItem k) {
                card.onyomi(k.getOnyomi())
                        .kunyomi(k.getKunyomi())
                        .strokeCount(k.getStrokeCount())
                        .radical(k.getRadical())
                        .mnemonic(k.getMnemonic());
            } else {
                card.reading(c.reading())
                        .exampleSentence(c.example())
                        .exampleMeaning(c.exampleMeaning())
                        .note(c.note());
            }
            cards.add(card.build());
        }
        return cards;
    }

    // ── Hỗ trợ: quyền và tiện ích ───────────────────────────────────────────

    private Deck requireVisibleDeck(Integer deckId, User student) {
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ thẻ"));
        if (!deck.isSystemDeck() && !isOwner(deck, student)) {
            throw new ResourceNotFoundException("Không tìm thấy bộ thẻ");
        }
        return deck;
    }

    private Deck requireOwnDeck(Integer deckId, User student) {
        Deck deck = requireVisibleDeck(deckId, student);
        if (!isOwner(deck, student)) {
            throw new UnauthorizedException("Bộ thẻ có sẵn không sửa được. Hãy tạo bộ riêng.");
        }
        return deck;
    }

    private static boolean isOwner(Deck deck, User student) {
        return deck.getOwner() != null && deck.getOwner().getUserId().equals(student.getUserId());
    }

    private SubjectLevel requireLevel(Integer levelId) {
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trình độ id=" + levelId));
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ học viên mới có lịch học thẻ");
        }
        return user;
    }

    private static String joinReadings(KanjiItem k) {
        List<String> parts = new ArrayList<>();
        if (k.getOnyomi() != null && !k.getOnyomi().isBlank()) parts.add(k.getOnyomi());
        if (k.getKunyomi() != null && !k.getKunyomi().isBlank()) parts.add(k.getKunyomi());
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static LocalDateTime startOfDay(LocalDateTime now) {
        return now.toLocalDate().atStartOfDay();
    }

    private static Map<String, Long> toUserCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], num(row[1]));
        }
        return map;
    }

    private static String key(StudyItemType type, Integer id) {
        return type + ":" + id;
    }

    private static long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
