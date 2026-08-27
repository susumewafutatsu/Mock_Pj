package com.example.demo.repository;

import com.example.demo.domain.model.ClassStudent;
import com.example.demo.domain.model.ClassStudentKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Danh sách học sinh trong lớp. Dùng để chặn học sinh lớp khác gọi thẳng
 * API bắt đầu thi bằng examId đoán được.
 */
@Repository
public interface ClassStudentRepository extends JpaRepository<ClassStudent, ClassStudentKey> {

    boolean existsById_ClassIdAndId_StudentId(Integer classId, String studentId);

    /**
     * Các lớp một học sinh đang học. Chỉ lấy ClassID chứ không nạp cả entity vì
     * chỗ dùng duy nhất là lọc danh sách đề theo lớp.
     */
    @Query("select cs.id.classId from ClassStudent cs where cs.id.studentId = :studentId")
    List<Integer> findClassIdsByStudentId(@Param("studentId") String studentId);
}
