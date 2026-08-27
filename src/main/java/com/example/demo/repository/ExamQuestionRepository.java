package com.example.demo.repository;

import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.ExamQuestionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, ExamQuestionKey> {

    List<ExamQuestion> findByExam_ExamIdOrderByQuestionOrderAsc(Integer examId);

    Optional<ExamQuestion> findByExam_ExamIdAndQuestion_QuestionId(Integer examId, Integer questionId);

    boolean existsByQuestion_QuestionId(Integer questionId);

    long countByExam_ExamId(Integer examId);

    /** Số câu của nhiều đề trong một query — dùng cho danh sách đề của học sinh. */
    @Query("""
            select eq.exam.examId as examId, count(eq) as total
            from ExamQuestion eq
            where eq.exam.examId in :examIds
            group by eq.exam.examId
            """)
    List<ExamQuestionCount> countByExamIdIn(@Param("examIds") Collection<Integer> examIds);

    /** Projection cho {@link #countByExamIdIn}. Đề không có câu hỏi sẽ không có dòng. */
    interface ExamQuestionCount {
        Integer getExamId();

        long getTotal();
    }
}
