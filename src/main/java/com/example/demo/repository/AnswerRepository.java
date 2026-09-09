package com.example.demo.repository;

import com.example.demo.domain.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Integer> {

    List<Answer> findByQuestion_QuestionId(Integer questionId);

    List<Answer> findByQuestion_QuestionIdAndIsCorrectTrue(Integer questionId);

    void deleteByQuestion_QuestionId(Integer questionId);

    /**
     * Nạp đáp án cho cả một loạt câu hỏi bằng một truy vấn.
     *
     * Dùng ở sổ tay câu sai: một trang 20 câu mà tra đáp án từng câu là 20
     * lượt truy vấn chỉ để dựng một màn hình.
     */
    List<Answer> findByQuestion_QuestionIdIn(Collection<Integer> questionIds);
}
