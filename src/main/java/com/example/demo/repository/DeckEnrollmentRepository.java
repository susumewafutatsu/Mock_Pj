package com.example.demo.repository;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.domain.model.DeckEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeckEnrollmentRepository extends JpaRepository<DeckEnrollment, DeckEnrollment.Key> {

    boolean existsByUserIdAndDeckId(String userId, Integer deckId);

    List<DeckEnrollment> findByUserId(String userId);

    /** Người đã ghi danh một bộ — để thẻ mới thêm vào bộ tự vào lịch của họ. */
    @Query("SELECT e.userId FROM DeckEnrollment e WHERE e.deckId = :deckId")
    List<String> findUserIdsByDeckId(@Param("deckId") Integer deckId);

    /** Thẻ này còn nằm trong bộ khác mà người đó đang học không. */
    @Query("""
            SELECT COUNT(di) > 0 FROM DeckItem di, DeckEnrollment e
            WHERE e.userId = :userId
              AND e.deckId = di.id.deckId
              AND di.id.deckId <> :exceptDeckId
              AND di.id.itemType = :itemType
              AND di.id.itemId = :itemId
            """)
    boolean itemInOtherEnrolledDeck(@Param("userId") String userId,
                                    @Param("exceptDeckId") Integer exceptDeckId,
                                    @Param("itemType") StudyItemType itemType,
                                    @Param("itemId") Integer itemId);
}
