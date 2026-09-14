package com.example.demo.repository;

import com.example.demo.domain.model.ReadingPassage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadingPassageRepository extends JpaRepository<ReadingPassage, Integer> {

    /** Đoạn văn trong một ngân hàng câu hỏi, mới nhất trước. */
    List<ReadingPassage> findByBank_BankIdOrderByPassageIdDesc(Integer bankId);
}
