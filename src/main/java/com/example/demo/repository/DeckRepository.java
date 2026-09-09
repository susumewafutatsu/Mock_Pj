package com.example.demo.repository;

import com.example.demo.domain.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeckRepository extends JpaRepository<Deck, Integer> {

    /**
     * Bộ thẻ mà một người được phép học: bộ dựng sẵn của hệ thống, bộ công
     * khai của người khác, và bộ riêng của chính mình.
     */
    @Query("""
            SELECT d FROM Deck d
            LEFT JOIN FETCH d.level lv
            WHERE d.owner IS NULL
               OR d.isPublic = true
               OR d.owner.userId = :userId
            ORDER BY lv.displayOrder ASC, d.deckId ASC
            """)
    List<Deck> findVisibleTo(@Param("userId") String userId);
}
