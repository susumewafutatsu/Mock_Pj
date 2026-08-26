package com.example.demo.repository;

import com.example.demo.domain.model.SubjectLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectLevelRepository extends JpaRepository<SubjectLevel, Integer> {

    List<SubjectLevel> findBySubject_SubjectIdOrderByDisplayOrderAsc(Integer subjectId);
}
