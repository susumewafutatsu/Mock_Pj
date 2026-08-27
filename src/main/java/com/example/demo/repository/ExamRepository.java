package com.example.demo.repository;

import com.example.demo.domain.model.Exam;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Integer> {
    List<Exam> findByCreatedByUserId(String teacherId);

    /**
     * Các đề một học sinh được nhìn thấy: đề của những lớp em có tên trong danh
     * sách, cộng các đề luyện tập tự do (ClassID null).
     *
     * {@code join fetch} để lấy luôn lớp / môn / trình độ / giáo viên trong một
     * câu truy vấn — danh sách đề cần mấy tên đó để hiển thị, không fetch sẵn
     * thì mỗi dòng lại thêm mấy query lazy.
     */
    @Query("""
            select e from Exam e
            left join fetch e.classEntity c
            left join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where c is null or c.classId in :classIds
            order by e.startTime desc, e.examId desc
            """)
    List<Exam> findVisibleToStudent(@Param("classIds") Collection<Integer> classIds);

    /**
     * Như trên nhưng cho học sinh chưa vào lớp nào: chỉ còn đề luyện tập tự do.
     * Tách riêng vì {@code in :classIds} với danh sách rỗng là SQL không hợp lệ
     * trên một số DB.
     */
    @Query("""
            select e from Exam e
            left join fetch e.classEntity c
            left join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where c is null
            order by e.startTime desc, e.examId desc
            """)
    List<Exam> findFreePracticeExams();

    /**
     * Khoá dòng đề thi để tuần tự hoá việc tạo phiên thi mới.
     *
     * Không có khoá này, hai request /start gần như cùng lúc (double-click, hai
     * tab) đều thấy "chưa có phiên" rồi cùng insert; một trong hai sẽ chết vì
     * UNIQUE(ExamID, StudentID) và học sinh nhìn thấy lỗi. Khoá xong thì request
     * thứ hai đọc được phiên vừa commit và chuyển sang luồng "tiếp tục làm bài".
     *
     * Chỉ dùng ở nhánh tạo mới; nhánh tiếp tục làm bài không chạm tới khoá.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exam e where e.examId = :examId")
    Optional<Exam> findByIdForUpdate(@Param("examId") Integer examId);
}