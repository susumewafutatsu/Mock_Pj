package com.example.demo.repository;

import com.example.demo.domain.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Integer> {

    List<Answer> findByQuestion_QuestionId(Integer questionId);

    List<Answer> findByQuestion_QuestionIdAndIsCorrectTrue(Integer questionId);

    void deleteByQuestion_QuestionId(Integer questionId);
}
