package com.example.demo.service.impl;

import com.example.demo.domain.model.QuestionBank;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.domain.model.User;
import com.example.demo.dto.request.QuestionBankCreateRequest;
import com.example.demo.dto.response.QuestionBankResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.QuestionBankRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.SubjectLevelRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.QuestionBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final QuestionBankRepository bankRepository;
    private final QuestionRepository questionRepository;
    private final SubjectLevelRepository levelRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<QuestionBankResponse> listMine(String teacherEmail) {
        String teacherId = requireTeacher(teacherEmail).getUserId();
        return bankRepository.findByTeacher_UserId(teacherId, Pageable.unpaged())
                .getContent().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public QuestionBankResponse create(QuestionBankCreateRequest request, String teacherEmail) {
        User teacher = requireTeacher(teacherEmail);

        SubjectLevel level = null;
        if (request.getLevelId() != null) {
            level = levelRepository.findById(request.getLevelId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy cấp độ id=" + request.getLevelId()));
        }

        QuestionBank bank = QuestionBank.builder()
                .teacher(teacher)
                .level(level)
                .title(request.getTitle())
                .sourceDocumentUrl(request.getSourceDocumentUrl())
                .build();
        return toResponse(bankRepository.save(bank));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private User requireTeacher(String teacherEmail) {
        return userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + teacherEmail));
    }

    private QuestionBankResponse toResponse(QuestionBank bank) {
        SubjectLevel level = bank.getLevel();
        return QuestionBankResponse.builder()
                .bankId(bank.getBankId())
                .title(bank.getTitle())
                .levelId(level != null ? level.getLevelId() : null)
                .levelName(level != null ? level.getLevelName() : null)
                .sourceDocumentUrl(bank.getSourceDocumentUrl())
                .createdAt(bank.getCreatedAt())
                .totalQuestions(
                        questionRepository.countByBank_BankIdAndIsDeletedFalse(bank.getBankId()))
                .build();
    }
}
