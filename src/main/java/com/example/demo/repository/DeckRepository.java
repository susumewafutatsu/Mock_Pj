package com.example.demo.repository;

import com.example.demo.domain.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeckRepository extends JpaRepository<Deck, Integer> {

    /** Bộ hệ thống và bộ của chính người học. */
    @Query("""
            SELECT d FROM Deck d
            LEFT JOIN FETCH d.level lv
            WHERE d.owner IS NULL
               OR d.owner.userId = :userId
            ORDER BY lv.displayOrder ASC, d.deckId ASC
            """)
    List<Deck> findVisibleTo(@Param("userId") String userId);

    /** Xoá bộ; khoá ngoại tự xoá DeckItems và DeckEnrollments. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Deck d WHERE d.deckId = :deckId")
    int deleteDeckById(@Param("deckId") Integer deckId);

    /** Bộ đang được gắn vào chặng lộ trình nào không. */
    @Query("SELECT COUNT(l) > 0 FROM CourseLesson l WHERE l.deck.deckId = :deckId")
    boolean usedByLesson(@Param("deckId") Integer deckId);
}
