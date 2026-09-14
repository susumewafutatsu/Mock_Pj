package com.example.demo.repository;

import com.example.demo.domain.enums.CourseStatus;
import com.example.demo.domain.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Integer> {

    /** Khoá do một người soạn, mới nhất lên trước. */
    @Query("""
            select c from Course c
            left join fetch c.level lv
            left join fetch lv.subject
            where c.author.userId = :authorId
            order by c.updatedAt desc
            """)
    List<Course> findByAuthor(@Param("authorId") String authorId);

    /** Hàng đợi duyệt của Admin, hoặc danh sách theo bất kỳ trạng thái nào. */
    @Query("""
            select c from Course c
            left join fetch c.author
            left join fetch c.level lv
            left join fetch lv.subject
            where c.status = :status
            order by c.updatedAt asc
            """)
    List<Course> findByStatus(@Param("status") CourseStatus status);

    /** Khoá thí sinh xem được — chỉ PUBLISHED, lọc tuỳ chọn theo trình độ. */
    @Query("""
            select c from Course c
            left join fetch c.author
            left join fetch c.level lv
            left join fetch lv.subject
            where c.status = com.example.demo.domain.enums.CourseStatus.PUBLISHED
              and (:levelId is null or lv.levelId = :levelId)
            order by lv.displayOrder asc, c.courseId asc
            """)
    List<Course> findPublished(@Param("levelId") Integer levelId);

    /** Nạp một tập khoá kèm tác giả / trình độ trong một câu — dùng ở "khoá của tôi". */
    @Query("""
            select c from Course c
            left join fetch c.author
            left join fetch c.level lv
            left join fetch lv.subject
            where c.courseId in :courseIds
            """)
    List<Course> findAllByIdWithDetails(@Param("courseIds") Collection<Integer> courseIds);

    long countByStatus(CourseStatus status);

    long countByAuthor_UserId(String authorId);


    /** Ô tìm kiếm: chỉ lộ trình đã xuất bản. */
    @Query("""
            select c from Course c
            where c.status = com.example.demo.domain.enums.CourseStatus.PUBLISHED
              and lower(c.title) like lower(concat('%', :q, '%'))
            order by c.courseId desc
            """)
    List<Course> searchPublished(@Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
