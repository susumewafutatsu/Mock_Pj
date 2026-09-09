package com.example.demo.repository;

import com.example.demo.domain.model.CourseEnrollment;
import com.example.demo.domain.model.CourseEnrollmentKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseEnrollmentRepository
        extends JpaRepository<CourseEnrollment, CourseEnrollmentKey> {

    boolean existsById_UserIdAndId_CourseId(String userId, Integer courseId);

    /** Các khoá một người đang theo. Chỉ lấy id vì phần chi tiết nạp riêng theo lô. */
    @Query("select e.id.courseId from CourseEnrollment e where e.id.userId = :userId")
    List<Integer> findCourseIdsByUserId(@Param("userId") String userId);

    long countById_CourseId(Integer courseId);
}
