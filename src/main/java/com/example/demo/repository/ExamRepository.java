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

    /**
     * Đề của một tập lớp. KHÔNG kèm đề luyện tập tự do.
     *
     * Trước đây chỗ này là một câu {@code where c is null or c.classId in (...)}
     * trộn cả hai loại đề vào một danh sách phẳng, và phía trên không còn cách
     * nào tách chúng ra ngoài việc đoán theo {@code className == null}. Hai loại
     * đề trả lời hai câu hỏi khác nhau của học sinh — "cô giao bài gì?" và "tự
     * ôn thì làm đề nào?" — nên giờ có hai câu truy vấn riêng.
     *
     * {@code join fetch} để lấy luôn lớp / môn / trình độ / giáo viên trong một
     * câu truy vấn — danh sách đề cần mấy tên đó để hiển thị, không fetch sẵn
     * thì mỗi dòng lại thêm mấy query lazy.
     *
     * Người gọi phải tự chặn danh sách rỗng: {@code in ()} là SQL không hợp lệ
     * trên một số DB.
     */
    @Query("""
            select e from Exam e
            join fetch e.classEntity c
            left join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where c.classId in :classIds
            """)
    List<Exam> findByClassIdIn(@Param("classIds") Collection<Integer> classIds);

    /**
     * Đề luyện tập tự do (ClassID null), lọc tuỳ chọn theo trình độ và môn học,
     * có phân trang.
     *
     * Truyền null cho tham số nào thì tham số đó không lọc — học sinh tự chọn
     * bộ lọc. Trước đây danh sách này đổ về TOÀN BỘ đề tự do trong hệ thống
     * trong một lần gọi, nên một em đang học N5 vẫn thấy đề luyện N1 của một
     * giáo viên hoàn toàn xa lạ, và response chỉ nặng thêm mãi theo thời gian.
     *
     * {@code countQuery} viết tay và KHÔNG có {@code fetch}: Spring Data tự suy
     * câu đếm từ câu chính sẽ kéo theo cả mấy mệnh đề fetch, vốn không hợp lệ
     * trong một câu {@code count}.
     *
     * Các {@code join fetch} ở đây đều là quan hệ ToOne nên không nhân dòng,
     * {@code limit} vẫn được đẩy xuống SQL. (Fetch một collection thì Hibernate
     * sẽ phải phân trang trong bộ nhớ — chỗ này không rơi vào trường hợp đó.)
     */
    @Query(value = """
            select e from Exam e
            left join fetch e.level l
            left join fetch l.subject s
            left join fetch e.createdBy
            where e.classEntity is null
              and (:levelId is null or l.levelId = :levelId)
              and (:subjectId is null or s.subjectId = :subjectId)
            """,
            countQuery = """
            select count(e) from Exam e
            left join e.level l
            left join l.subject s
            where e.classEntity is null
              and (:levelId is null or l.levelId = :levelId)
              and (:subjectId is null or s.subjectId = :subjectId)
            """)
    Page<Exam> findPracticeExams(@Param("levelId") Integer levelId,
                                 @Param("subjectId") Integer subjectId,
                                 Pageable pageable);

    /**
     * Đề luyện tập tự do thuộc một tập trình độ — dùng cho phần gợi ý ở trang
     * tổng hợp, nơi bộ lọc mặc định là các trình độ học sinh đang học.
     *
     * Người gọi phải tự chặn danh sách rỗng.
     */
    @Query("""
            select e from Exam e
            join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where e.classEntity is null and l.levelId in :levelIds
            """)
    List<Exam> findPracticeExamsByLevelIdIn(@Param("levelIds") Collection<Integer> levelIds);

    /**
     * Các trình độ thực sự CÓ đề luyện tập, kèm số lượng.
     *
     * Bộ lọc được dựng từ đây chứ không phải từ toàn bộ danh mục trình độ, để
     * học sinh không bấm phải một lựa chọn rồi nhận về danh sách rỗng.
     */
    @Query("""
            select l.levelId as levelId, l.levelName as levelName,
                   s.subjectId as subjectId, s.subjectName as subjectName,
                   count(e) as total
            from Exam e
            join e.level l
            join l.subject s
            where e.classEntity is null
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