package com.example.demo.repository.specification;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.domain.model.Question;
import com.example.demo.domain.model.Tag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Các điều kiện lọc câu hỏi, ghép động bằng Specification.
 * <p>
 * Điều kiện tag dùng EXISTS subquery thay vì JOIN trực tiếp: nếu JOIN bảng QuestionTags
 * thì một câu hỏi có N tag khớp sẽ bị trả về N lần, và {@code DISTINCT} kết hợp với
 * {@code LIMIT/OFFSET} của phân trang sẽ cho ra tổng số bản ghi sai.
 */
public final class QuestionSpecifications {

    private QuestionSpecifications() {
    }

    /** Câu hỏi có ít nhất một trong các tag được yêu cầu. */
    public static Specification<Question> hasAnyTag(Collection<String> tagNames) {
        return (root, query, cb) -> {
            if (tagNames == null || tagNames.isEmpty()) {
                return null;
            }
            return cb.exists(tagSubquery(root, query, cb, tagNames));
        };
    }

    /** Câu hỏi có đủ tất cả các tag được yêu cầu (mỗi tag một EXISTS riêng, nối bằng AND). */
    public static Specification<Question> hasAllTags(Collection<String> tagNames) {
        return (root, query, cb) -> {
            if (tagNames == null || tagNames.isEmpty()) {
                return null;
            }
            List<Predicate> predicates = new ArrayList<>();
            for (String tagName : tagNames) {
                predicates.add(cb.exists(tagSubquery(root, query, cb, List.of(tagName))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Subquery<Integer> tagSubquery(
            Root<Question> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Collection<String> tagNames) {

        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Question> subRoot = subquery.from(Question.class);
        Join<Question, Tag> tagJoin = subRoot.join("tags");

        return subquery
                .select(cb.literal(1))
                .where(
                        cb.equal(subRoot.get("questionId"), root.get("questionId")),
                        cb.lower(tagJoin.get("tagName")).in(tagNames)
                );
    }

    /** Tìm trong nội dung câu hỏi. {@code keyword} phải được escape LIKE trước khi truyền vào. */
    public static Specification<Question> contentContains(String likeSafeKeyword) {
        return (root, query, cb) -> likeSafeKeyword == null
                ? null
                : cb.like(cb.lower(root.get("content")), "%" + likeSafeKeyword + "%", '\\');
    }

    public static Specification<Question> hasSubject(Integer subjectId) {
        return (root, query, cb) -> subjectId == null
                ? null
                : cb.equal(root.get("bank").get("level").get("subject").get("subjectId"), subjectId);
    }

    public static Specification<Question> hasLevel(Integer levelId) {
        return (root, query, cb) -> levelId == null
                ? null
                : cb.equal(root.get("bank").get("level").get("levelId"), levelId);
    }

    public static Specification<Question> hasBank(Integer bankId) {
        return (root, query, cb) -> bankId == null
                ? null
                : cb.equal(root.get("bank").get("bankId"), bankId);
    }

    public static Specification<Question> hasDifficulty(Integer difficulty) {
        return (root, query, cb) -> difficulty == null
                ? null
                : cb.equal(root.get("difficultyLevel"), difficulty);
    }

    public static Specification<Question> hasQuestionType(QuestionType questionType) {
        return (root, query, cb) -> questionType == null
                ? null
                : cb.equal(root.get("questionType"), questionType);
    }

    public static Specification<Question> isAiGenerated(Boolean isAiGenerated) {
        return (root, query, cb) -> isAiGenerated == null
                ? null
                : cb.equal(root.get("isAiGenerated"), isAiGenerated);
    }
}
