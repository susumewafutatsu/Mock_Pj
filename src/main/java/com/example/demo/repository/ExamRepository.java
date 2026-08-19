package com.example.demo.repository;

import com.example.demo.domain.model.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Integer> {
    List<Exam> findByCreatedByUserId(String teacherId);
}