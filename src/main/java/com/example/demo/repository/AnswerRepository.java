package com.example.demo.repository;

import com.example.demo.domain.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// TODO: Add custom query methods:
// - findByQuestionId(Long questionId)
// - findByQuestionIdAndIsCorrect(Long questionId, boolean isCorrect)
@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

}
