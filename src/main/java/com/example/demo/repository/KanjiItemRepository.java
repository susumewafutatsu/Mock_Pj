package com.example.demo.repository;

import com.example.demo.domain.model.KanjiItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface KanjiItemRepository extends JpaRepository<KanjiItem, Integer> {

    /** Nạp cả loạt chữ Hán cho một phiên ôn thẻ bằng một truy vấn. */
    List<KanjiItem> findByKanjiIdIn(Collection<Integer> ids);

    List<KanjiItem> findByLevel_LevelId(Integer levelId);
}
