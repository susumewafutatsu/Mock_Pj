package com.example.demo.repository;

import com.example.demo.domain.model.CourseLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CourseLessonRepository extends JpaRepository<CourseLesson, Integer> {

    List<CourseLesson> findByCourse_CourseIdOrderByOrderNoAsc(Integer courseId);

    long countByCourse_CourseId(Integer courseId);

    /** Số bài của cả một tập khoá trong một câu truy vấn. */
    @Query("""
            select l.course.courseId, count(l)
            from CourseLesson l
            where l.course.courseId in :courseIds
            group by l.course.courseId
            """)
    List<Object[]> countByCourseIds(@Param("courseIds") Collection<Integer> courseIds);
}
