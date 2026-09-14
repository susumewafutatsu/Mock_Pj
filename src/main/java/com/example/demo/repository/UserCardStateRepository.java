package com.example.demo.repository;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.domain.model.UserCardState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserCardStateRepository extends JpaRepository<UserCardState, Integer> {

    /** Lọc theo bộ khi :deckId khác null. */
    String IN_DECK = " (:deckId IS NULL OR EXISTS (SELECT 1 FROM DeckItem di WHERE di.id.deckId = :deckId"
            + " AND di.id.itemType = cs.itemType AND di.id.itemId = cs.itemId)) ";

    Optional<UserCardState> findByUser_UserIdAndItemTypeAndItemId(
            String userId, StudyItemType itemType, Integer itemId);

    /** Những thẻ đã có trong lịch trong một nhóm thẻ cho trước. */
    @Query("""
            SELECT cs FROM UserCardState cs
            WHERE cs.user.userId = :userId
              AND cs.itemType = :itemType
              AND cs.itemId IN :itemIds
            """)
    List<UserCardState> findEnrolled(@Param("userId") String userId,
                                     @Param("itemType") StudyItemType itemType,
                                     @Param("itemIds") Collection<Integer> itemIds);

    /** Thẻ ôn đến hạn (đã từng học), quá hạn lâu nhất trước. */
    @Query("SELECT cs FROM UserCardState cs WHERE cs.user.userId = :userId"
            + " AND cs.firstReviewedAt IS NOT NULL AND cs.dueAt <= :now AND" + IN_DECK
            + "ORDER BY cs.dueAt ASC")
    List<UserCardState> findDueReviews(@Param("userId") String userId,
                                       @Param("now") LocalDateTime now,
                                       @Param("deckId") Integer deckId,
                                       Pageable pageable);

    @Query("SELECT COUNT(cs) FROM UserCardState cs WHERE cs.user.userId = :userId"
            + " AND cs.firstReviewedAt IS NOT NULL AND cs.dueAt <= :now AND" + IN_DECK)
    long countDueReviews(@Param("userId") String userId,
                         @Param("now") LocalDateTime now,
                         @Param("deckId") Integer deckId);

    /** Thẻ mới chưa học, theo thứ tự thêm vào lịch. */
    @Query("SELECT cs FROM UserCardState cs WHERE cs.user.userId = :userId"
            + " AND cs.firstReviewedAt IS NULL AND" + IN_DECK + "ORDER BY cs.cardStateId ASC")
    List<UserCardState> findNewCards(@Param("userId") String userId,
                                     @Param("deckId") Integer deckId,
                                     Pageable pageable);

    @Query("SELECT COUNT(cs) FROM UserCardState cs WHERE cs.user.userId = :userId"
            + " AND cs.firstReviewedAt IS NULL AND" + IN_DECK)
    long countNewCards(@Param("userId") String userId, @Param("deckId") Integer deckId);

    /** Số thẻ mới đã học từ một mốc (thường là đầu ngày). */
    @Query("""
            SELECT COUNT(cs) FROM UserCardState cs
            WHERE cs.user.userId = :userId AND cs.firstReviewedAt >= :since
            """)
    long countStudiedSince(@Param("userId") String userId, @Param("since") LocalDateTime since);

    long countByUser_UserId(String userId);

    /** Số thẻ đã thuộc (khoảng cách ôn từ 21 ngày). */
    @Query("""
            SELECT COUNT(cs) FROM UserCardState cs
            WHERE cs.user.userId = :userId AND cs.intervalDays >= :matureDays
            """)
    long countMature(@Param("userId") String userId, @Param("matureDays") int matureDays);

    /** Tiến độ từng bộ: [deckId, thẻ trong lịch, đã thuộc, cần ôn, thẻ mới]. */
    @Query("""
            SELECT di.id.deckId,
                   COUNT(cs),
                   SUM(CASE WHEN cs.intervalDays >= :matureDays THEN 1 ELSE 0 END),
                   SUM(CASE WHEN cs.firstReviewedAt IS NOT NULL AND cs.dueAt <= :now THEN 1 ELSE 0 END),
                   SUM(CASE WHEN cs.firstReviewedAt IS NULL THEN 1 ELSE 0 END)
            FROM DeckItem di
            JOIN UserCardState cs
              ON cs.itemType = di.id.itemType
             AND cs.itemId = di.id.itemId
             AND cs.user.userId = :userId
            WHERE di.id.deckId IN :deckIds
            GROUP BY di.id.deckId
            """)
    List<Object[]> findDeckProgress(@Param("userId") String userId,
                                    @Param("deckIds") Collection<Integer> deckIds,
                                    @Param("matureDays") int matureDays,
                                    @Param("now") LocalDateTime now);

    /** Trạng thái của người học trên các thẻ của một bộ. */
    @Query("""
            SELECT cs FROM UserCardState cs, DeckItem di
            WHERE di.id.deckId = :deckId
              AND cs.itemType = di.id.itemType
              AND cs.itemId = di.id.itemId
              AND cs.user.userId = :userId
            """)
    List<UserCardState> findInDeck(@Param("userId") String userId, @Param("deckId") Integer deckId);

    @Modifying
    @Query("DELETE FROM UserCardState cs WHERE cs.user.userId = :userId AND cs.itemType = :itemType AND cs.itemId = :itemId")
    int deleteForUser(@Param("userId") String userId,
                      @Param("itemType") StudyItemType itemType,
                      @Param("itemId") Integer itemId);

    @Modifying
    @Query("DELETE FROM UserCardState cs WHERE cs.itemType = :itemType AND cs.itemId = :itemId")
    int deleteForItem(@Param("itemType") StudyItemType itemType, @Param("itemId") Integer itemId);

    /** [userId, thẻ ôn đến hạn] cho job nhắc ôn. */
    @Query("""
            SELECT cs.user.userId, COUNT(cs) FROM UserCardState cs
            WHERE cs.firstReviewedAt IS NOT NULL AND cs.dueAt <= :now
            GROUP BY cs.user.userId
            """)
    List<Object[]> countDueReviewsByUser(@Param("now") LocalDateTime now);

    /** [userId, thẻ mới chưa học]. */
    @Query("""
            SELECT cs.user.userId, COUNT(cs) FROM UserCardState cs
            WHERE cs.firstReviewedAt IS NULL
            GROUP BY cs.user.userId
            """)
    List<Object[]> countNewByUser();

    /** [userId, thẻ mới đã học từ mốc]. */
    @Query("""
            SELECT cs.user.userId, COUNT(cs) FROM UserCardState cs
            WHERE cs.firstReviewedAt >= :since
            GROUP BY cs.user.userId
            """)
    List<Object[]> countStudiedSinceByUser(@Param("since") LocalDateTime since);
}
