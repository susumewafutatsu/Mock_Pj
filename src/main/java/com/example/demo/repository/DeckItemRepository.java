package com.example.demo.repository;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.domain.model.DeckItem;
import com.example.demo.domain.model.DeckItemKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DeckItemRepository extends JpaRepository<DeckItem, DeckItemKey> {

    List<DeckItem> findByDeck_DeckIdOrderByOrderNoAsc(Integer deckId);

    long countByDeck_DeckId(Integer deckId);

    /** Số thẻ của nhiều bộ: [deckId, số thẻ]. */
    @Query("""
            SELECT di.deck.deckId, COUNT(di)
            FROM DeckItem di
            WHERE di.deck.deckId IN :deckIds
            GROUP BY di.deck.deckId
            """)
    List<Object[]> countByDeckIds(@Param("deckIds") Collection<Integer> deckIds);

    @Query("SELECT COALESCE(MAX(di.orderNo), 0) FROM DeckItem di WHERE di.id.deckId = :deckId")
    int findMaxOrderNo(@Param("deckId") Integer deckId);

    /** Id các thẻ cùng loại đã có trong bộ. */
    @Query("""
            SELECT di.id.itemId FROM DeckItem di
            WHERE di.id.deckId = :deckId AND di.id.itemType = :itemType AND di.id.itemId IN :ids
            """)
    List<Integer> findItemIdsInDeck(@Param("deckId") Integer deckId,
                                    @Param("itemType") StudyItemType itemType,
                                    @Param("ids") Collection<Integer> ids);

    @Modifying
    @Query("DELETE FROM DeckItem di WHERE di.id.itemType = :itemType AND di.id.itemId = :itemId")
    int deleteByItem(@Param("itemType") StudyItemType itemType, @Param("itemId") Integer itemId);
}
