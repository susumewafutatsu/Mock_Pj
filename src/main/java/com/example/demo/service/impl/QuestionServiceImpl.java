package com.example.demo.service.impl;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.model.Answer;
import com.example.demo.domain.model.Question;
import com.example.demo.domain.model.QuestionBank;
import com.example.demo.dto.request.AnswerPayload;
import com.example.demo.dto.request.QuestionCreateRequest;
import com.example.demo.dto.request.QuestionSearchRequest;
import com.example.demo.dto.request.QuestionUpdateRequest;
import com.example.demo.dto.response.AnswerResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.QuestionResponse;
import com.example.demo.dto.response.QuestionSummaryResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.AnswerRepository;
import com.example.demo.repository.QuestionBankRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.TagRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.specification.QuestionSpecifications;
import com.example.demo.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionBankRepository bankRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public QuestionResponse create(QuestionCreateRequest req, String teacherEmail) {
        QuestionBank bank = requireOwnedBank(req.getBankId(), teacherEmail);
        validateAnswers(req.getQuestionType(), req.getAnswers());

        Question question = Question.builder()
                .bank(bank)
                .content(req.getContent())
                .questionType(req.getQuestionType())
                .difficultyLevel(req.getDifficultyLevel())
                .explanation(req.getExplanation())
                .isAiGenerated(req.isAiGenerated())
                .isDeleted(false)
                .build();
        questionRepository.save(question);

        List<Answer> answers = req.getAnswers().stream()
                .map(p -> Answer.builder()
                        .question(question)
                        .answerContent(p.getAnswerContent())
                        .isCorrect(p.isCorrect())
                        .build())
                .toList();
        answerRepository.saveAll(answers);

        return toResponse(question, answers, false);
    }

    @Override
    @Transactional
    public QuestionResponse update(Integer bankId, Integer questionId,
                                   QuestionUpdateRequest req, String teacherEmail) {
        requireOwnedBank(bankId, teacherEmail);
        Question question = requireQuestion(bankId, questionId);
        validateAnswers(req.getQuestionType(), req.getAnswers());

        // Sửa thoải mái: đề thi đã phát hành đọc snapshot trong ExamQuestions,
        // không đọc bảng này, nên điểm đã chấm không bị ảnh hưởng.
        question.setContent(req.getContent());
        question.setQuestionType(req.getQuestionType());
        question.setDifficultyLevel(req.getDifficultyLevel());
        question.setExplanation(req.getExplanation());

        List<Answer> answers = syncAnswers(question, req.getAnswers());
        boolean usedInExam = questionRepository.isUsedInAnyExam(questionId);
        return toResponse(question, answers, usedInExam);
    }

    /**
     * Đồng bộ danh sách đáp án về đúng như client gửi lên: cập nhật cái có
     * answerId, thêm cái mới, xoá cái không còn trong danh sách.
     */
    private List<Answer> syncAnswers(Question question, List<AnswerPayload> payloads) {
        List<Answer> existing = answerRepository.findByQuestion_QuestionId(question.getQuestionId());
        Map<Integer, Answer> byId = new HashMap<>();
        existing.forEach(a -> byId.put(a.getAnswerId(), a));

        List<Answer> result = new ArrayList<>();
        for (AnswerPayload p : payloads) {
            Answer target = p.getAnswerId() == null ? null : byId.remove(p.getAnswerId());
            if (target == null) {
                target = Answer.builder().question(question).build();
            }
            target.setAnswerContent(p.getAnswerContent());
            target.setIsCorrect(p.isCorrect());
            result.add(target);
        }
        answerRepository.saveAll(result);

        // Còn lại trong byId là đáp án client đã bỏ đi.
        // An toàn: snapshot của các đề cũ đã giữ bản sao riêng, và
        // ExamQuestionAnswers.OriginalAnswerID được khai báo ON DELETE SET NULL.
        if (!byId.isEmpty()) {
            answerRepository.deleteAll(byId.values());
        }
        return result;
    }

    @Override
    @Transactional
    public void delete(Integer bankId, Integer questionId, String teacherEmail) {
        requireOwnedBank(bankId, teacherEmail);
        Question question = requireQuestion(bankId, questionId);

        if (questionRepository.isUsedInAnyExam(questionId)) {
            // Xoá mềm: giữ dòng Questions để ExamQuestions.QuestionID (nằm trong
            // khoá chính) và các báo cáo thống kê không bị hỏng.
            question.setIsDeleted(true);
            return;
        }
        answerRepository.deleteByQuestion_QuestionId(questionId);
        questionRepository.delete(question);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionResponse> listByBank(Integer bankId, String teacherEmail, Pageable pageable) {
        requireOwnedBank(bankId, teacherEmail);
        return questionRepository.findByBank_BankIdAndIsDeletedFalse(bankId, pageable)
                .map(this::toResponseWithAnswers);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionResponse getOne(Integer bankId, Integer questionId, String teacherEmail) {
        requireOwnedBank(bankId, teacherEmail);
        return toResponseWithAnswers(requireQuestion(bankId, questionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionResponse> filterByDifficulty(Integer bankId, int minDifficulty,
                                                     int maxDifficulty, String teacherEmail) {
        requireOwnedBank(bankId, teacherEmail);
        return questionRepository
                .findByBank_BankIdAndIsDeletedFalseAndDifficultyLevelBetween(
                        bankId, minDifficulty, maxDifficulty)
                .stream()
                .map(this::toResponseWithAnswers)
                .collect(Collectors.toList());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Trả về ngân hàng câu hỏi nếu người gọi đúng là chủ sở hữu.
     * Ném 404 (không phải 403) khi không phải chủ, để không tiết lộ rằng
     * bankId đó tồn tại và thuộc về giáo viên khác.
     */
    private QuestionBank requireOwnedBank(Integer bankId, String teacherEmail) {
        String teacherId = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + teacherEmail))
                .getUserId();

        return bankRepository.findByBankIdAndTeacher_UserId(bankId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy ngân hàng câu hỏi id=" + bankId));
    }

    private Question requireQuestion(Integer bankId, Integer questionId) {
        return questionRepository
                .findByQuestionIdAndBank_BankIdAndIsDeletedFalse(questionId, bankId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy câu hỏi id=" + questionId));
    }

    private void validateAnswers(QuestionType type, List<AnswerPayload> answers) {
        if (type == QuestionType.ESSAY) {
            if (!answers.isEmpty()) {
                throw new BusinessException("Câu tự luận không được có danh sách đáp án");
            }
            return;
        }
        if (answers.size() < 2) {
            throw new BusinessException("Câu hỏi trắc nghiệm cần ít nhất 2 đáp án");
        }
        long correctCount = answers.stream().filter(AnswerPayload::isCorrect).count();
        if (correctCount == 0) {
            throw new BusinessException("Phải có ít nhất một đáp án đúng");
        }
        if (type == QuestionType.MULTIPLE_CHOICE && correctCount > 1) {
            throw new BusinessException("Câu MULTIPLE_CHOICE chỉ được có một đáp án đúng");
        }
    }

    private QuestionResponse toResponseWithAnswers(Question q) {
        List<Answer> answers = answerRepository.findByQuestion_QuestionId(q.getQuestionId());
        return toResponse(q, answers, questionRepository.isUsedInAnyExam(q.getQuestionId()));
    }

    private QuestionResponse toResponse(Question q, List<Answer> answers, boolean usedInExam) {
        return QuestionResponse.builder()
                .questionId(q.getQuestionId())
                .bankId(q.getBank().getBankId())
                .content(q.getContent())
                .questionType(q.getQuestionType())
                .difficultyLevel(q.getDifficultyLevel())
                .explanation(q.getExplanation())
                .aiGenerated(Boolean.TRUE.equals(q.getIsAiGenerated()))
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .usedInExam(usedInExam)
                .answers(answers.stream()
                        .map(a -> AnswerResponse.builder()
                                .answerId(a.getAnswerId())
                                .answerContent(a.getAnswerContent())
                                .correct(Boolean.TRUE.equals(a.getIsCorrect()))
                                .build())
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestionSummaryResponse> searchQuestions(
            QuestionSearchRequest request, Pageable pageable) {

        Set<String> tags = request.normalizedTags();

        // Tag yêu cầu nhưng không tồn tại trong bảng Tags thì chắc chắn không có câu hỏi nào khớp.
        // Chặn sớm ở đây để khỏi chạy query nặng lên bảng Questions.
        if (!tags.isEmpty() && !hasMatchableTags(tags, request.resolvedTagMode())) {
            return PageResponse.empty(pageable);
        }

        Specification<Question> spec = buildSpecification(request, tags);
        Page<Question> page = questionRepository.findAll(spec, pageable);

        return PageResponse.from(page, QuestionSummaryResponse::from);
    }

    /**
     * ANY: cần ít nhất 1 tag tồn tại. ALL: cần tất cả tag đều tồn tại.
     */
    private boolean hasMatchableTags(Set<String> tags, QuestionSearchRequest.TagMode mode) {
        Set<String> existing = Set.copyOf(tagRepository.findExistingTagNames(tags));
        return mode == QuestionSearchRequest.TagMode.ALL
                ? existing.containsAll(tags)
                : !existing.isEmpty();
    }

    private Specification<Question> buildSpecification(QuestionSearchRequest request, Set<String> tags) {
        Specification<Question> tagSpec = request.resolvedTagMode() == QuestionSearchRequest.TagMode.ALL
                ? QuestionSpecifications.hasAllTags(tags)
                : QuestionSpecifications.hasAnyTag(tags);

        return Specification.where(tagSpec)
                .and(QuestionSpecifications.contentContains(request.likeSafeKeyword()))
                .and(QuestionSpecifications.hasSubject(request.getSubjectId()))
                .and(QuestionSpecifications.hasLevel(request.getLevelId()))
                .and(QuestionSpecifications.hasBank(request.getBankId()))
                .and(QuestionSpecifications.hasDifficulty(request.getDifficulty()))
                .and(QuestionSpecifications.hasQuestionType(request.getQuestionType()))
                .and(QuestionSpecifications.isAiGenerated(request.getIsAiGenerated()));
    }
}
