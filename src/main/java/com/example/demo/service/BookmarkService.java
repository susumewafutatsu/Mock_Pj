package com.example.demo.service;

import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.Bookmark;
import com.example.demo.domain.model.Question;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.BookmarkResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.BookmarkRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Câu đã đánh dấu + ghi chú của học viên. */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    /** Ghi chú dài hơn thế này là đang viết bài luận, không phải ghi chú. */
    private static final int NOTE_MAX = 2000;

    private final BookmarkRepository bookmarkRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionDetailRepository detailRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BookmarkResponse> list(String email) {
        User user = requireUser(email);
        return bookmarkRepository.findByUser(user.getUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Đánh dấu (hoặc sửa ghi chú nếu đã đánh dấu). */
    @Transactional
    public BookmarkResponse upsert(String email, Integer questionId, String note) {
        User user = requireUser(email);
        requireSeen(user, questionId);

        String cleaned = note == null ? null : note.trim();
        if (cleaned != null && cleaned.isEmpty()) {
            cleaned = null;
        }
        if (cleaned != null && cleaned.length() > NOTE_MAX) {
            cleaned = cleaned.substring(0, NOTE_MAX);
        }

        Bookmark bookmark = bookmarkRepository
                .findByUser_UserIdAndQuestion_QuestionId(user.getUserId(), questionId)
                .orElseGet(() -> Bookmark.builder()
                        .user(user)
                        .question(questionRepository.getReferenceById(questionId))
                        .build());
        bookmark.setNote(cleaned);
        bookmark.setUpdatedAt(LocalDateTime.now());
        return toResponse(bookmarkRepository.save(bookmark));
    }

    @Transactional
    public void remove(String email, Integer questionId) {
        User user = requireUser(email);
        bookmarkRepository.findByUser_UserIdAndQuestion_QuestionId(user.getUserId(), questionId)
                .ifPresent(bookmarkRepository::delete);
    }

    private void requireSeen(User user, Integer questionId) {
        boolean seen = detailRepository.existsBySubmission_Student_UserIdAndQuestion_QuestionIdAndSubmission_StatusNot(
                user.getUserId(), questionId, SubmissionStatus.IN_PROGRESS);
        if (!seen) {
            // "Không tìm thấy" chứ không phải "không có quyền": câu chưa từng làm
            // thì với người gọi, nó không tồn tại.
            throw new ResourceNotFoundException(
                    "Chỉ đánh dấu được câu hỏi trong những bài bạn đã nộp.");
        }
    }

    private BookmarkResponse toResponse(Bookmark bookmark) {
        Question q = bookmark.getQuestion();
        return BookmarkResponse.builder()
                .bookmarkId(bookmark.getBookmarkId())
                .questionId(q.getQuestionId())
                .content(q.getContent())
                .questionType(q.getQuestionType())
                .skill(q.getSkill())
                .note(bookmark.getNote())
                .createdAt(bookmark.getCreatedAt())
                .updatedAt(bookmark.getUpdatedAt())
                .build();
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
    }
}
