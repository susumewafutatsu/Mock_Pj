package com.example.demo.repository;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.domain.model.UserCardState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserCardStateRepository extends JpaRepository<UserCardState, Integer> {

    Optional<UserCardState> findByUser_UserIdAndItemTypeAndItemId(
            String userId, StudyItemType itemType, Integer itemId);

    /**
     * Những thẻ đã ghi danh trong một nhóm thẻ cho trước.
     *
     * Dùng lúc ghi danh bộ thẻ để biết thẻ nào đã học rồi mà bỏ qua — người
     * học bộ "Bài 1" rồi mới học bộ "Tổng hợp" có chung mấy thẻ thì tiến độ
     * cũ phải được giữ nguyên, không bị đặt lại về thẻ mới.
     */
    @Query("""
            SELECT cs FROM UserCardState cs
            WHERE cs.user.userId = :userId
              AND cs.itemType = :itemType
              AND cs.itemId IN :itemIds
            """)
    List<UserCardState> findEnrolled(@Param("userId") String userId,
                                     @Param("itemType") StudyItemType itemType,
                                     @Param("itemIds") Collection<Integer> itemIds);

    /**
     * Hàng đợi ôn hôm nay: thẻ đã tới hạn, thẻ quá hạn lâu nhất lên trước.
     *
     * Giới hạn số lượng do tầng service đặt qua Pageable — ôn dồn 500 thẻ một
     * ngày là cách nhanh nhất khiến người học bỏ cuộc.
     */
    @Query("""
            SELECT cs FROM UserCardState cs
            WHERE cs.user.userId = :userId
              AND cs.dueAt <= :now
            ORDER BY cs.dueAt ASC
            """)
    List<UserCardState> findDue(@Param("userId") String userId,
                                @Param("now") LocalDateTime now,
                                org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT COUNT(cs) FROM UserCardState cs
            WHERE cs.user.userId = :userId AND cs.dueAt <= :now
            """)
    long countDue(@Param("userId") String userId, @Param("now") LocalDateTime now);

    long countByUser_UserId(String userId);

    /** Số thẻ đã vào trí nhớ dài hạn (khoảng cách ôn từ 21 ngày trở lên). */
    @Query("""
            SELECT COUNT(cs) FROM UserCardState cs
            WHERE cs.user.userId = :userId AND cs.intervalDays >= :matureDays
            """)
    long countMature(@Param("userId") String userId, @Param("matureDays") int matureDays);

    /**
     * Tiến độ của từng bộ thẻ trong một lần truy vấn: đã ghi danh bao nhiêu
     * thẻ và thuộc được bao nhiêu.
     *
     * @return từng dòng là [deckId, số thẻ đã ghi danh, số thẻ đã thuộc]
     */
    @Query("""
            SELECT di.deck.deckId,
                   COUNT(cs),
                   SUM(CASE WHEN cs.intervalDays >= :matureDays THEN 1 ELSE 0 END)
            FROM DeckItem di
            JOIN UserCardState cs
              ON cs.itemType = di.id.itemType
             AND cs.itemId = di.id.itemId
             AND cs.user.userId = :userId
            WHERE di.deck.deckId IN :deckIds
            GROUP BY di.deck.deckId
            """)
    List<Object[]> findDeckProgress(@Param("userId") String userId,
                                    @Param("deckIds") Collection<Integer> deckIds,
                                    @Param("matureDays") int matureDays);
}
