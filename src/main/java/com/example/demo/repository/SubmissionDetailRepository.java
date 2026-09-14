package com.example.demo.repository;

import com.example.demo.domain.model.SubmissionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionDetailRepository extends JpaRepository<SubmissionDetail, Integer> {

    List<SubmissionDetail> findBySubmission_SubmissionId(Integer submissionId);

    long countBySubmission_SubmissionIdAndIsCorrect(Integer submissionId, boolean isCorrect);

    /** Câu hỏi này đã có thí sinh trả lời chưa. */
    boolean existsByQuestion_QuestionId(Integer questionId);

    /** Dòng cần upsert khi autosave: một câu hỏi chỉ có một dòng trong một bài làm. */
    Optional<SubmissionDetail> findBySubmission_SubmissionIdAndQuestion_QuestionId(
            Integer submissionId, Integer questionId);

    /** Số câu đúng của cả một tập bài làm trong một câu truy vấn. */
    @Query("""
            select d.submission.submissionId, count(d) from SubmissionDetail d
            where d.submission.submissionId in :ids and d.isCorrect = true
            group by d.submission.submissionId
            """)
    List<Object[]> countCorrectBySubmissionIdIn(@Param("ids") Collection<Integer> ids);

    // Thống kê cho người ra đề

    /** Tỉ lệ đúng của từng câu trong một đề. */
    @Query("""
            select d.question.questionId, count(d),
                   sum(case when d.isCorrect = true then 1 else 0 end)
            from SubmissionDetail d
            where d.submission.exam.examId = :examId
              and d.submission.status <> com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
            group by d.question.questionId
            """)
    List<Object[]> questionStatsByExam(@Param("examId") Integer examId);

    /** Như trên nhưng chỉ tính thí sinh của một phòng. */
    @Query("""
            select d.question.questionId, count(d),
                   sum(case when d.isCorrect = true then 1 else 0 end)
            from SubmissionDetail d
            where d.submission.exam.examId = :examId
              and d.submission.status <> com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
              and d.submission.student.userId in :studentIds
            group by d.question.questionId
            """)
    List<Object[]> questionStatsByExamAndStudents(@Param("examId") Integer examId,
                                                  @Param("studentIds") Collection<String> studentIds);

    /** Tỉ lệ đúng theo TAG của câu hỏi. */
    @Query("""
            select t.tagId, t.tagName, count(d),
                   sum(case when d.isCorrect = true then 1 else 0 end)
            from SubmissionDetail d
            join d.question q
            join q.tags t
            where d.submission.exam.examId = :examId
              and d.submission.status <> com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
            group by t.tagId, t.tagName
            """)
    List<Object[]> tagStatsByExam(@Param("examId") Integer examId);

    /** Như trên nhưng chỉ tính thí sinh của một phòng. */
    @Query("""
            select t.tagId, t.tagName, count(d),
                   sum(case when d.isCorrect = true then 1 else 0 end)
            from SubmissionDetail d
            join d.question q
            join q.tags t
            where d.submission.exam.examId = :examId
              and d.submission.status <> com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
              and d.submission.student.userId in :studentIds
            group by t.tagId, t.tagName
            """)
    List<Object[]> tagStatsByExamAndStudents(@Param("examId") Integer examId,
                                             @Param("studentIds") Collection<String> studentIds);


    // ── Gợi ý ôn theo điểm yếu của MỘT học viên ─────────────────────────────

    /** Tỉ lệ đúng theo kỹ năng JLPT trên mọi bài đã nộp. @return [JlptSkill, số lượt, số đúng] */
    @Query("""
            select q.skill, count(d), sum(case when d.isCorrect = true then 1 else 0 end)
            from SubmissionDetail d join d.question q
            where d.submission.student.userId = :userId
              and d.submission.status <> com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
              and q.skill is not null
            group by q.skill
            """)
    List<Object[]> skillStatsOfStudent(@Param("userId") String userId);

    /** Tỉ lệ đúng theo tag trên mọi bài đã nộp. @return [tagId, tên tag, số lượt, số đúng] */
    @Query("""
            select t.tagId, t.tagName, count(d), sum(case when d.isCorrect = true then 1 else 0 end)
            from SubmissionDetail d join d.question q join q.tags t
            where d.submission.student.userId = :userId
              and d.submission.status <> com.example.demo.domain.enums.SubmissionStatus.IN_PROGRESS
            group by t.tagId, t.tagName
            """)
    List<Object[]> tagStatsOfStudent(@Param("userId") String userId);

    /** Tỉ lệ đúng theo CẤP của ngân hàng chứa câu hỏi, trong một bài làm — dùng cho bài xếp trình độ. */
    @Query("""
            select l.levelName, l.displayOrder, count(d), sum(case when d.isCorrect = true then 1 else 0 end)
            from SubmissionDetail d join d.question q join q.bank b join b.level l
            where d.submission.submissionId = :submissionId
            group by l.levelName, l.displayOrder
            order by l.displayOrder asc
            """)
    List<Object[]> levelStatsOfSubmission(@Param("submissionId") Integer submissionId);

    /** Học viên đã làm câu này trong một bài ĐÃ NỘP chưa — chốt quyền đánh dấu câu. */
    boolean existsBySubmission_Student_UserIdAndQuestion_QuestionIdAndSubmission_StatusNot(
            String userId, Integer questionId, com.example.demo.domain.enums.SubmissionStatus status);


    /** Số câu đã trả lời của một tập bài làm. @return [submissionId, số câu] */
    @Query("""
            select d.submission.submissionId, count(d) from SubmissionDetail d
            where d.submission.submissionId in :ids
              and (d.selectedSnapshotAnswer is not null or d.essayResponse is not null)
            group by d.submission.submissionId
            """)
    List<Object[]> countAnsweredBySubmissionIdIn(@Param("ids") Collection<Integer> ids);
}
