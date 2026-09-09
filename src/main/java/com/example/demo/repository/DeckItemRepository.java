package com.example.demo.repository;

import com.example.demo.domain.model.DeckItem;
import com.example.demo.domain.model.DeckItemKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DeckItemRepository extends JpaRepository<DeckItem, DeckItemKey> {

    List<DeckItem> findByDeck_DeckIdOrderByOrderNoAsc(Integer deckId);

    long countByDeck_DeckId(Integer deckId);

    /**
     * Đếm thẻ cho nhiều bộ cùng lúc — dùng ở màn danh sách bộ thẻ, nơi mỗi
     * thẻ bộ phải hiện "n thẻ". Đếm từng bộ một là N+1 truy vấn.
     *
     * @return từng dòng là [deckId, số thẻ]
     */
    @Query("""
            SELECT di.deck.deckId, COUNT(di)
            FROM DeckItem di
            WHERE di.deck.deckId IN :deckIds
            GROUP BY di.deck.deckId
            """)
    List<Object[]> countByDeckIds(@Param("deckIds") Collection<Integer> deckIds);
}
