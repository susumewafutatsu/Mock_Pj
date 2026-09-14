package com.example.demo.service.impl;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.Answer;
import com.example.demo.domain.model.MistakeEntry;
import com.example.demo.domain.model.Question;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.MistakeAttemptRequest;
import com.example.demo.dto.response.MistakeAttemptResponse;
import com.example.demo.dto.response.MistakeBookResponse;
import com.example.demo.dto.response.MistakeEntryResponse;
import com.example.demo.dto.response.StudyOptionView;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.AnswerRepository;
import com.example.demo.repository.MistakeEntryRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.MistakeBookService;
import com.example.demo.util.DbTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Cài đặt sổ tay câu sai. */
@Service
@RequiredArgsConstructor
public class MistakeBookServiceImpl implements MistakeBookService {

    private static final Logger log = LoggerFactory.getLogger(MistakeBookServiceImpl.class);

    /** Trần kích thước trang, chặn việc gọi size rất lớn để kéo cả sổ tay về. */
    private static final int MAX_PAGE_SIZE = 50;

    private final MistakeEntryRepository mistakeRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public int recordMistakes(User student, Collection<Integer> wrongQuestionIds,
                              LocalDateTime now) {
        if (student == null || wrongQuestionIds == null || wrongQuestionIds.isEmpty()) {
            return 0;
        }
        // Bỏ trùng trước khi truy vấn: một đề về lý thuyết không lặp câu hỏi, nhưng dữ liệu cũ có thể có.
        Set<Integer> questionIds = wrongQuestionIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (questionIds.isEmpty()) {
            return 0;
        }

        // Một truy vấn cho cả đề thay vì mỗi câu một lượt.
        Map<Integer, MistakeEntry> existing = mistakeRepository
                .findByUser_UserIdAndQuestion_QuestionIdIn(student.getUserId(), questionIds)
                .stream()
                .collect(Collectors.toMap(m -> m.getQuestion().getQuestionId(), m -> m));

        List<MistakeEntry> toSave = new ArrayList<>();
        for (Integer questionId : questionIds) {
            MistakeEntry entry = existing.get(questionId);
            if (entry == null) {
                // Chỉ cần khoá chính để JPA dựng được tham chiếu — không phải
                // nạp cả Question từ DB chỉ để ghi một khoá ngoại.
                entry = MistakeEntry.builder()
                        .user(student)
                        .question(Question.builder().questionId(questionId).build())
                        .wrongCount(0)
                        .correctStreak(0)
                        .build();
            }
            entry.recordWrong(now);
            toSave.add(entry);
        }
        mistakeRepository.saveAll(toSave);

        log.debug("Ghi sổ tay câu sai: userId={} số câu={}", student.getUserId(), toSave.size());
        return toSave.size();
    }

    @Override
    @Transactional(readOnly = true)
    public MistakeBookResponse getMistakeBook(String studentEmail, int page, int size) {
        User student = requireStudent(studentEmail);
        LocalDateTime now = DbTime.now();

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<MistakeEntry> found = mistakeRepository.findOpenMistakes(
                student.getUserId(), now, PageRequest.of(safePage, safeSize));

        List<MistakeEntry> entries = found.getContent();
        Map<Integer, List<StudyOptionView>> optionsByQuestion = loadOptions(entries);

        List<MistakeEntryResponse> items = entries.stream()
                .map(entry -> toResponse(entry, optionsByQuestion, now))
                .toList();

        return MistakeBookResponse.builder()
                .items(items)
                .totalOpen(found.getTotalElements())
                .totalDue(mistakeRepository.countDue(student.getUserId(), now))
                .totalMastered(mistakeRepository
                        .countByUser_UserIdAndMasteredAtIsNotNull(student.getUserId()))
                .page(safePage)
                .size(safeSize)
                .totalPages(found.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public MistakeAttemptResponse attempt(Integer questionId, MistakeAttemptRequest request,
                                          String studentEmail) {
        User student = requireStudent(studentEmail);
        LocalDateTime now = DbTime.now();

        // Tra theo cặp (người, câu hỏi) chứ không tra theo mistakeId.
        MistakeEntry entry = mistakeRepository
                .findByUser_UserIdAndQuestion_QuestionId(student.getUserId(), questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Câu hỏi này không có trong sổ tay câu sai của bạn"));

        Question question = entry.getQuestion();
        QuestionType type = question.getQuestionType() == null
                ? QuestionType.MULTIPLE_CHOICE : question.getQuestionType();

        boolean correct;
        Integer correctAnswerId = null;

        if (type == QuestionType.ESSAY) {
            // Không có đáp án để máy so.
            correct = Boolean.TRUE.equals(request == null ? null : request.getSelfCorrect());
        } else {
            Integer selectedId = request == null ? null : request.getSelectedAnswerId();
            if (selectedId == null) {
                throw new BusinessException("Chưa chọn đáp án");
            }
            List<Answer> answers = answerRepository.findByQuestion_QuestionId(questionId);
            // Lựa chọn phải thuộc đúng câu hỏi này.
            Answer selected = answers.stream()
                    .filter(a -> a.getAnswerId().equals(selectedId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "Đáp án không thuộc câu hỏi này"));
            correct = Boolean.TRUE.equals(selected.getIsCorrect());
            correctAnswerId = answers.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getIsCorrect()))
                    .map(Answer::getAnswerId)
                    .findFirst()
                    .orElse(null);
        }

        boolean mastered = false;
        if (correct) {
            mastered = entry.recordCorrect(now);
        } else {
            entry.recordWrong(now);
        }
        mistakeRepository.save(entry);

        return MistakeAttemptResponse.builder()
                .questionId(questionId)
                .correct(correct)
                .correctAnswerId(correctAnswerId)
                .explanation(question.getExplanation())
                .correctStreak(entry.getCorrectStreak())
                .mastered(mastered)
                .nextReviewAt(entry.getNextReviewAt())
                .remainingOpen(mistakeRepository.countOpen(student.getUserId()))
                .build();
    }

    // ── Hỗ trợ ──────────────────────────────────────────────────────────────

    /** Nạp lựa chọn cho cả trang bằng một truy vấn, tránh N+1. */
    private Map<Integer, List<StudyOptionView>> loadOptions(List<MistakeEntry> entries) {
        List<Integer> mcQuestionIds = entries.stream()
                .map(MistakeEntry::getQuestion)
                .filter(q -> q.getQuestionType() != QuestionType.ESSAY)
                .map(Question::getQuestionId)
                .toList();
        if (mcQuestionIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<StudyOptionView>> grouped = new HashMap<>();
        for (Answer answer : answerRepository.findByQuestion_QuestionIdIn(mcQuestionIds)) {
            grouped.computeIfAbsent(answer.getQuestion().getQuestionId(), k -> new ArrayList<>())
                    // Cố ý không map isCorrect — xem chú thích ở StudyOptionView.
                    .add(StudyOptionView.builder()
                            .answerId(answer.getAnswerId())
                            .content(answer.getAnswerContent())
                            .build());
        }
        // Thứ tự ổn định giữa các lần tải; nếu không, mỗi lần mở lại là các lựa
        // chọn nhảy chỗ và người học tưởng đề đã đổi.
        grouped.values().forEach(list -> list.sort(Comparator.comparing(StudyOptionView::getAnswerId)));
        return grouped;
    }

    private MistakeEntryResponse toResponse(MistakeEntry entry,
                                            Map<Integer, List<StudyOptionView>> options,
                                            LocalDateTime now) {
        Question question = entry.getQuestion();
        return MistakeEntryResponse.builder()
                .questionId(question.getQuestionId())
                .content(question.getContent())
                .questionType(question.getQuestionType())
                .difficultyLevel(question.getDifficultyLevel())
                .wrongCount(entry.getWrongCount())
                .correctStreak(entry.getCorrectStreak())
                .lastWrongAt(entry.getLastWrongAt())
                .nextReviewAt(entry.getNextReviewAt())
                .due(entry.isDueAt(now))
                .options(options.getOrDefault(question.getQuestionId(), List.of()))
                .build();
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ thí sinh mới có sổ tay câu sai");
        }
        return user;
    }
}
