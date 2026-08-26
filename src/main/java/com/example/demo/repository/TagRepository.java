package com.example.demo.repository;

import com.example.demo.domain.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Integer> {

    /** Trả về các tag thực sự tồn tại trong số tên tag được yêu cầu (so sánh không phân biệt hoa/thường). */
    @Query("SELECT LOWER(t.tagName) FROM Tag t WHERE LOWER(t.tagName) IN :tagNames")
    List<String> findExistingTagNames(@Param("tagNames") Collection<String> tagNames);
}
