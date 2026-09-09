package com.example.demo.repository;

import com.example.demo.domain.model.VocabItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface VocabItemRepository extends JpaRepository<VocabItem, Integer> {

    /** Nạp cả loạt từ vựng cho một phiên ôn thẻ bằng một truy vấn. */
    List<VocabItem> findByVocabIdIn(Collection<Integer> ids);

    List<VocabItem> findByLevel_LevelId(Integer levelId);
}
