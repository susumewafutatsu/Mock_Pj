package com.example.demo.repository;

import com.example.demo.domain.model.MistakeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MistakeEntryRepository extends JpaRepository<MistakeEntry, Integer> {

    /** Dòng cần cập nhật khi thí sinh lại sai đúng câu đó. */
    Optional<MistakeEntry> findByUser_UserIdAndQuestion_QuestionId(String userId, Integer questionId);

    /** Nạp sẵn cả loạt dòng của một người cho những câu vừa chấm xong. */
    List<MistakeEntry> findByUser_UserIdAndQuestion_QuestionIdIn(
            String userId, Collection<Integer> questionIds);

    /** Hàng đợi ôn tập: câu chưa sửa được, sắp theo mức độ cấp thiết. */
    @Query(value = """
            SELECT m FROM MistakeEntry m
            JOIN FETCH m.question q
            WHERE m.user.userId = :userId
              AND m.masteredAt IS NULL
              AND q.isDeleted = false
            ORDER BY
              CASE WHEN m.nextReviewAt IS NULL OR m.nextReviewAt <= :now THEN 0 ELSE 1 END,
              m.wrongCount DESC,
              m.nextReviewAt ASC
            """,
            // Phải khai countQuery riêng: Spring Data không suy ra được câu đếm từ một truy vấn có JOIN FETCH.
            countQuery = """
            SELECT COUNT(m) FROM MistakeEntry m
            JOIN m.question q
            WHERE m.user.userId = :userId
              AND m.masteredAt IS NULL
              AND q.isDeleted = false
            """)
    Page<MistakeEntry> findOpenMistakes(@Param("userId") String userId,
                                        @Param("now") LocalDateTime now,
                                        Pageable pageable);

    /** Số câu chưa sửa được — hiện lên huy hiệu ở thanh điều hướng. */
    @Query("""
            SELECT COUNT(m) FROM MistakeEntry m
            WHERE m.user.userId = :userId
              AND m.masteredAt IS NULL
              AND m.question.isDeleted = false
            """)
    long countOpen(@Param("userId") String userId);

    /** Số câu đã tới hạn ôn ngay bây giờ. */
    @Query("""
            SELECT COUNT(m) FROM MistakeEntry m
            WHERE m.user.userId = :userId
              AND m.masteredAt IS NULL
              AND m.question.isDeleted = false
              AND (m.nextReviewAt IS NULL OR m.nextReviewAt <= :now)
            """)
    long countDue(@Param("userId") String userId, @Param("now") LocalDateTime now);

    /** Số câu đã sửa được — con số để người học thấy mình tiến bộ. */
    long countByUser_UserIdAndMasteredAtIsNotNull(String userId);
}
