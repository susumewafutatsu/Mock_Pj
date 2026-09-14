package com.example.demo.repository;

import com.example.demo.domain.model.KanjiItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface KanjiItemRepository extends JpaRepository<KanjiItem, Integer> {

    /** Nạp cả loạt chữ Hán cho một phiên ôn thẻ bằng một truy vấn. */
    List<KanjiItem> findByKanjiIdIn(Collection<Integer> ids);

    List<KanjiItem> findByLevel_LevelId(Integer levelId);

    /** Tìm chữ Hán theo mặt chữ, âm đọc hoặc nghĩa. */
    @Query("""
            SELECT k FROM KanjiItem k LEFT JOIN FETCH k.level
            WHERE LOWER(k.glyph) LIKE :q OR LOWER(k.onyomi) LIKE :q
               OR LOWER(k.kunyomi) LIKE :q OR LOWER(k.meaning) LIKE :q
            ORDER BY k.kanjiId
            """)
    List<KanjiItem> search(@Param("q") String q, Pageable pageable);
}
