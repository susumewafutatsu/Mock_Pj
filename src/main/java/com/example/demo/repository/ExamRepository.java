package com.example.demo.repository;

import com.example.demo.domain.model.Exam;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Integer> {
    List<Exam> findByCreatedByUserId(String teacherId);

    /** Đề của một tập phòng thi. */
    @Query("""
            select distinct e from Exam e
            join RoomExam re on re.id.examId = e.examId
            left join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where re.id.roomId in :roomIds
            """)
    List<Exam> findByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds);

    /** Đề công khai, lọc tuỳ chọn theo trình độ và môn học, có phân trang. */
    @Query(value = """
            select e from Exam e
            left join fetch e.level l
            left join fetch l.subject s
            left join fetch e.createdBy
            where e.isPublic = true
              and (:levelId is null or l.levelId = :levelId)
              and (:subjectId is null or s.subjectId = :subjectId)
            """,
            countQuery = """
            select count(e) from Exam e
            left join e.level l
            left join l.subject s
            where e.isPublic = true
              and (:levelId is null or l.levelId = :levelId)
              and (:subjectId is null or s.subjectId = :subjectId)
            """)
    Page<Exam> findPracticeExams(@Param("levelId") Integer levelId,
                                 @Param("subjectId") Integer subjectId,
                                 Pageable pageable);

    /** Đề công khai thuộc một tập trình độ. */
    @Query("""
            select e from Exam e
            join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where e.isPublic = true and l.levelId in :levelIds
            """)
    List<Exam> findPracticeExamsByLevelIdIn(@Param("levelIds") Collection<Integer> levelIds);

    /** Các trình độ thực sự CÓ đề công khai, kèm số lượng. */
    @Query("""
            select l.levelId as levelId, l.levelName as levelName,
                   s.subjectId as subjectId, s.subjectName as subjectName,
                   count(e) as total
            from Exam e
            join e.level l
            join l.subject s
            where e.isPublic = true
            group by l.levelId, l.levelName, s.subjectId, s.subjectName, l.displayOrder
            order by s.subjectName asc, l.displayOrder asc
            """)
    List<PracticeLevelCount> countPracticeExamsByLevel();

    /** Một dòng của {@link #countPracticeExamsByLevel()}. */
    interface PracticeLevelCount {
        Integer getLevelId();
        String getLevelName();
        Integer getSubjectId();
        String getSubjectName();
        long getTotal();
    }

    /** Khoá dòng đề thi để tuần tự hoá việc tạo phiên thi mới. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exam e where e.examId = :examId")
    Optional<Exam> findByIdForUpdate(@Param("examId") Integer examId);

    // ── Trang quản trị ──────────────────────────────────────────────────────

    long countByIsPublicTrue();

    long countByCreatedBy_UserId(String userId);


    /** Bài xếp trình độ đang dùng — bài mới nhất được đánh dấu và công khai. */
    Optional<Exam> findFirstByIsPlacementTrueAndIsPublicTrueOrderByExamIdDesc();

    /** Ô tìm kiếm: chỉ đề công khai — đề trong phòng là lịch thi của người khác. */
    @Query("""
            select e from Exam e
            where e.isPublic = true
              and lower(e.title) like lower(concat('%', :q, '%'))
            order by e.examId desc
            """)
    List<Exam> searchPublic(@Param("q") String q, Pageable pageable);
}
