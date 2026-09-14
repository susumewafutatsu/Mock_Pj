package com.example.demo.controller;

import com.example.demo.domain.model.QuestionBank;
import com.example.demo.domain.model.ReadingPassage;
import com.example.demo.dto.request.ReadingPassageRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.ReadingPassageResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.QuestionBankRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.ReadingPassageRepository;
import com.example.demo.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bài đọc (読解) trong ngân hàng câu hỏi. */
@RestController
@RequestMapping("/api/teacher/question-banks/{bankId}/passages")
@RequiredArgsConstructor
public class TeacherReadingPassageController {

    private final ReadingPassageRepository passageRepository;
    private final QuestionBankRepository bankRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<ReadingPassageResponse>> list(
            @PathVariable Integer bankId,
            @AuthenticationPrincipal UserDetails me) {
        requireOwnedBank(bankId, me.getUsername());
        return ApiResponse.success(
                passageRepository.findByBank_BankIdOrderByPassageIdDesc(bankId).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<ReadingPassageResponse>> create(
            @PathVariable Integer bankId,
            @Valid @RequestBody ReadingPassageRequest request,
            @AuthenticationPrincipal UserDetails me) {
        QuestionBank bank = requireOwnedBank(bankId, me.getUsername());
        ReadingPassage passage = passageRepository.save(ReadingPassage.builder()
                .bank(bank)
                .title(trimToNull(request.getTitle()))
                .content(request.getContent().trim())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo bài đọc", toResponse(passage)));
    }

    @PutMapping("/{passageId}")
    @Transactional
    public ApiResponse<ReadingPassageResponse> update(
            @PathVariable Integer bankId,
            @PathVariable Integer passageId,
            @Valid @RequestBody ReadingPassageRequest request,
            @AuthenticationPrincipal UserDetails me) {
        requireOwnedBank(bankId, me.getUsername());
        ReadingPassage passage = requirePassage(bankId, passageId);
        passage.setTitle(trimToNull(request.getTitle()));
        passage.setContent(request.getContent().trim());
        // Đề đã phát hành không bị ảnh hưởng.
        return ApiResponse.success("Đã cập nhật bài đọc", toResponse(passageRepository.save(passage)));
    }

    @DeleteMapping("/{passageId}")
    @Transactional
    public ApiResponse<Void> delete(
            @PathVariable Integer bankId,
            @PathVariable Integer passageId,
            @AuthenticationPrincipal UserDetails me) {
        requireOwnedBank(bankId, me.getUsername());
        ReadingPassage passage = requirePassage(bankId, passageId);

        long inUse = countQuestions(passageId);
        if (inUse > 0) {
            // Khoá ngoại là SET NULL nên xoá vẫn chạy được.
            throw new BusinessException("Bài đọc này đang được " + inUse
                    + " câu hỏi dùng. Gỡ các câu đó ra khỏi bài đọc trước khi xoá.");
        }
        passageRepository.delete(passage);
        return ApiResponse.success("Đã xoá bài đọc", null);
    }

    // ── Phần dùng chung ─────────────────────────────────────────────────────

    private ReadingPassageResponse toResponse(ReadingPassage passage) {
        return ReadingPassageResponse.builder()
                .passageId(passage.getPassageId())
                .bankId(passage.getBank().getBankId())
                .title(passage.getTitle())
                .content(passage.getContent())
                .createdAt(passage.getCreatedAt())
                .questionCount(countQuestions(passage.getPassageId()))
                .build();
    }

    private long countQuestions(Integer passageId) {
        return questionRepository.countByPassage_PassageIdAndIsDeletedFalse(passageId);
    }

    private ReadingPassage requirePassage(Integer bankId, Integer passageId) {
        ReadingPassage passage = passageRepository.findById(passageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bài đọc id=" + passageId));
        if (!passage.getBank().getBankId().equals(bankId)) {
            throw new ResourceNotFoundException("Không tìm thấy bài đọc id=" + passageId);
        }
        return passage;
    }

    /** Ném "không tìm thấy" chứ không phải "không có quyền" — xem QuestionServiceImpl. */
    private QuestionBank requireOwnedBank(Integer bankId, String teacherEmail) {
        String teacherId = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + teacherEmail))
                .getUserId();
        return bankRepository.findByBankIdAndTeacher_UserId(bankId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy ngân hàng câu hỏi id=" + bankId));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
