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
     * Đề của một tập phòng thi. KHÔNG kèm đề công khai.
     *
     * Trước đây đề gắn thẳng vào lớp qua {@code Exams.ClassID}, nên chỗ này chỉ
     * cần lọc theo cột đó. Giờ quan hệ đi qua {@link com.example.demo.domain.model.RoomExam},
     * nên phải join — đổi lại, một đề dùng được ở nhiều phòng.
     *
     * {@code distinct} là bắt buộc: cùng một đề gắn vào hai phòng mà thí sinh
     * đều là thành viên sẽ ra hai dòng giống hệt nhau.
     *
     * Người gọi phải tự chặn danh sách rỗng: {@code in ()} là SQL không hợp lệ
     * trên một số DB.
     */
    @Query("""
            select distinct e from Exam e
            join RoomExam re on re.id.examId = e.examId
            left join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where re.id.roomId in :roomIds
            """)
    List<Exam> findByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds);

    /**
     * Đề công khai, lọc tuỳ chọn theo trình độ và môn học, có phân trang.
     *
     * Truyền null cho tham số nào thì tham số đó không lọc — thí sinh tự chọn
     * bộ lọc. Danh sách này trước đây đổ về TOÀN BỘ đề tự do trong hệ thống
     * trong một lần gọi, nên một người đang học N5 vẫn thấy đề luyện N1 của
     * một người ra đề hoàn toàn xa lạ, và response chỉ nặng thêm mãi.
     *
     * Điều kiện {@code e.isPublic = true} thay cho {@code e.classEntity is null}
     * cũ — xem chú thích ở {@link Exam#getIsPublic()} về việc vì sao quy ước
     * ngầm đó phải được nói thành lời.
     *
     * {@code countQuery} viết tay và KHÔNG có {@code fetch}: Spring Data tự suy
     * câu đếm từ câu chính sẽ kéo theo cả mấy mệnh đề fetch, vốn không hợp lệ
     * trong một câu {@code count}.
     */
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

    /**
     * Đề công khai thuộc một tập trình độ — dùng cho phần gợi ý ở trang tổng
     * hợp, nơi bộ lọc mặc định là các trình độ thí sinh đang theo.
     *
     * Người gọi phải tự chặn danh sách rỗng.
     */
    @Query("""
            select e from Exam e
            join fetch e.level l
            left join fetch l.subject
            left join fetch e.createdBy
            where e.isPublic = true and l.levelId in :levelIds
            """)
    List<Exam> findPracticeExamsByLevelIdIn(@Param("levelIds") Collection<Integer> levelIds);

    /**
     * Các trình độ thực sự CÓ đề công khai, kèm số lượng.
     *
     * Bộ lọc được dựng từ đây chứ không phải từ toàn bộ danh mục trình độ, để
     * thí sinh không bấm phải một lựa chọn rồi nhận về danh sách rỗng.
     */
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

    /**
     * Khoá dòng đề thi để tuần tự hoá việc tạo phiên thi mới.
     *
     * Không có khoá này, hai request /start gần như cùng lúc (double-click, hai
     * tab) đều thấy "chưa có phiên" rồi cùng insert; một trong hai sẽ chết vì
     * UNIQUE(ExamID, StudentID, AttemptNumber) và thí sinh nhìn thấy lỗi. Khoá
     * xong thì request thứ hai đọc được phiên vừa commit và chuyển sang luồng
     * "tiếp tục làm bài".
     *
     * Chỉ dùng ở nhánh tạo mới; nhánh tiếp tục làm bài không chạm tới khoá.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exam e where e.examId = :examId")
    Optional<Exam> findByIdForUpdate(@Param("examId") Integer examId);
}
