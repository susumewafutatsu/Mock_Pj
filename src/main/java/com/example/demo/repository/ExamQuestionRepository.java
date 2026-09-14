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

    /** Số câu của nhiều đề trong một query — dùng cho danh sách đề của thí sinh. */
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


    /** Đề công khai có câu mang tag này — gợi ý "làm đề nhắm đúng chỗ yếu". Bỏ bài xếp trình độ. */
    @org.springframework.data.jpa.repository.Query("""
            select distinct eq.exam from ExamQuestion eq join eq.question q join q.tags t
            where t.tagId = :tagId and eq.exam.isPublic = true and eq.exam.isPlacement = false
            """)
    java.util.List<com.example.demo.domain.model.Exam> findPublicExamsByTag(
            @org.springframework.data.repository.query.Param("tagId") Integer tagId);
}
