package com.example.demo.repository;

import com.example.demo.domain.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// TODO: Add custom query methods:
// - findByQuestionBankId(Long bankId)
// - findByDifficultyBetween(int min, int max)
// - findByQuestionBankIdAndDifficulty(Long bankId, int difficulty)
@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

}
