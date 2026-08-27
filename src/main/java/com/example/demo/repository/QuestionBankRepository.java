package com.example.demo.repository;

import com.example.demo.domain.model.QuestionBank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Integer> {

    Page<QuestionBank> findByTeacher_UserId(String teacherId, Pageable pageable);

    Page<QuestionBank> findByTeacher_UserIdAndLevel_LevelId(
            String teacherId, Integer levelId, Pageable pageable);

    Optional<QuestionBank> findByBankIdAndTeacher_UserId(Integer bankId, String teacherId);

    boolean existsByBankIdAndTeacher_UserId(Integer bankId, String teacherId);
}
