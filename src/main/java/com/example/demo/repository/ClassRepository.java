package com.example.demo.repository;

import com.example.demo.domain.model.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ClassRepository extends JpaRepository<ClassEntity, Integer> {
    List<ClassEntity> findByTeacherUserId(String teacherId);

    /**
     * Nạp một tập lớp kèm giáo viên / trình độ / môn học trong một câu truy vấn.
     *
     * Có riêng phương thức này vì trang của học sinh luôn cần hiển thị tên môn
     * và tên giáo viên cho từng lớp; để lazy thì mỗi lớp lại thêm ba query.
     *
     * Người gọi phải tự chặn danh sách rỗng: {@code in ()} là SQL không hợp lệ
     * trên một số DB.
     */
    @Query("""
            select c from ClassEntity c
            left join fetch c.teacher
            left join fetch c.level l
            left join fetch l.subject
            where c.classId in :classIds
            """)
    List<ClassEntity> findAllByIdWithDetails(@Param("classIds") Collection<Integer> classIds);
}