package com.example.demo.repository;

import com.example.demo.domain.model.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClassRepository extends JpaRepository<ClassEntity, Integer> {
    List<ClassEntity> findByTeacherUserId(String teacherId);
}