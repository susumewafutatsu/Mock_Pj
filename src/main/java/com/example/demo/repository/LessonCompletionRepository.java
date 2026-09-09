package com.example.demo.repository;

import com.example.demo.domain.model.LessonCompletion;
import com.example.demo.domain.model.LessonCompletionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LessonCompletionRepository
        extends JpaRepository<LessonCompletion, LessonCompletionKey> {

    /** Bài đã hoàn thành của một người trong một khoá — dùng để đánh dấu trên danh sách bài. */
    @Query("""
            select c.id.lessonId from LessonCompletion c
            where c.id.userId = :userId and c.lesson.course.courseId = :courseId
            """)
    List<Integer> findCompletedLessonIds(@Param("userId") String userId,
                                         @Param("courseId") Integer courseId);

    /**
     * Số bài đã hoàn thành của một người, gom theo khoá, trong một câu truy vấn.
     *
     * Đây là tử số của phần trăm tiến độ; mẫu số lấy từ
     * {@link CourseLessonRepository#countByCourseIds}.
     *
     * @return từng dòng là [courseId, số bài đã xong]
     */
    @Query("""
            select c.lesson.course.courseId, count(c)
            from LessonCompletion c
            where c.id.userId = :userId
              and c.lesson.course.courseId in :courseIds
            group by c.lesson.course.courseId
            """)
    List<Object[]> countCompletedByCourseIds(@Param("userId") String userId,
                                             @Param("courseIds") Collection<Integer> courseIds);
}
