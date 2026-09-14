package com.example.demo.repository;

import com.example.demo.domain.model.VocabItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface VocabItemRepository extends JpaRepository<VocabItem, Integer> {

    /** Nạp cả loạt từ vựng cho một phiên ôn thẻ bằng một truy vấn. */
    List<VocabItem> findByVocabIdIn(Collection<Integer> ids);

    List<VocabItem> findByLevel_LevelId(Integer levelId);

    /** Tìm từ theo chữ viết, cách đọc hoặc nghĩa. */
    @Query("""
            SELECT v FROM VocabItem v LEFT JOIN FETCH v.level
            WHERE LOWER(v.word) LIKE :q OR LOWER(v.reading) LIKE :q OR LOWER(v.meaning) LIKE :q
            ORDER BY v.vocabId
            """)
    List<VocabItem> search(@Param("q") String q, Pageable pageable);
}
