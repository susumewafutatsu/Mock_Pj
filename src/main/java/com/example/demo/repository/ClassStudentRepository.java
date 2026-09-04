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

    /**
     * Tất cả học sinh trong một lớp (giáo viên xem danh sách).
     */
    List<ClassStudent> findByClassEntity_ClassId(Integer classId);

    /**
     * Sĩ số của lớp — dùng trong ClassResponse để tránh load toàn bộ entity.
     */
    long countByClassEntity_ClassId(Integer classId);

    /**
     * Sĩ số của cả một tập lớp trong một câu truy vấn.
     *
     * Trang "lớp của tôi" trước đây gọi {@link #countByClassEntity_ClassId} cho
     * từng lớp, cộng thêm một lượt load toàn bộ danh sách học sinh của lớp chỉ
     * để lấy ra ClassEntity — ba lớp là bảy câu truy vấn. Gộp lại còn một.
     *
     * Người gọi phải tự chặn danh sách rỗng.
     */
    @Query("""
            select cs.id.classId as classId, count(cs) as total
            from ClassStudent cs
            where cs.id.classId in :classIds
            group by cs.id.classId
            """)
    List<ClassHeadcount> countByClassIdIn(@Param("classIds") java.util.Collection<Integer> classIds);

    /** Một dòng của {@link #countByClassIdIn}. */
    interface ClassHeadcount {
        Integer getClassId();
        long getTotal();
    }
}
