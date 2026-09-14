package com.example.demo.repository;

import com.example.demo.domain.model.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Integer> {

    @Query("""
            select b from Bookmark b
            join fetch b.question
            where b.user.userId = :userId
            order by b.bookmarkId desc
            """)
    List<Bookmark> findByUser(@Param("userId") String userId);

    Optional<Bookmark> findByUser_UserIdAndQuestion_QuestionId(String userId, Integer questionId);

    /** Câu nào trong một tập đã được đánh dấu — để trang xem lại bài tô sẵn ngôi sao. */
    @Query("""
            select b.question.questionId from Bookmark b
            where b.user.userId = :userId and b.question.questionId in :questionIds
            """)
    List<Integer> findMarkedQuestionIds(@Param("userId") String userId,
                                        @Param("questionIds") Collection<Integer> questionIds);
}
