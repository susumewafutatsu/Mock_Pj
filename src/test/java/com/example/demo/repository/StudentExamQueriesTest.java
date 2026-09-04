package com.example.demo.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Các câu truy vấn mới của màn hình chọn đề có THỰC SỰ chạy được trên MySQL không.
 *
 * Hibernate phân tích cú pháp JPQL lúc khởi động, nên {@code contextLoads} đã
 * bắt được lỗi viết sai. Nhưng nó không phát hiện được lỗi chỉ lộ ra lúc sinh
 * SQL và gửi xuống database — mà đúng chỗ này thì có hai kiểu dễ vấp:
 *
 * <ul>
 *   <li>Điều kiện {@code :param is null or cột = :param} dùng để bỏ qua bộ lọc:
 *       nếu Hibernate không suy được kiểu của tham số null thì lỗi chỉ nổ khi
 *       học sinh mở trang mà không chọn bộ lọc — tức là ngay lần đầu vào.</li>
 *   <li>Mệnh đề {@code in :ids} với danh sách rỗng sinh ra {@code in ()}, là SQL
 *       không hợp lệ. Các phương thức này đều yêu cầu người gọi tự chặn danh
 *       sách rỗng, nên ở đây chỉ kiểm nhánh có phần tử.</li>
 * </ul>
 *
 * Test chạy trên database phát triển và không ghi gì — chỉ cần các câu truy vấn
 * trả về mà không ném exception. Không khẳng định gì về dữ liệu vì database phát
 * triển của mỗi người một khác.
 */
@SpringBootTest
@Transactional(readOnly = true)
class StudentExamQueriesTest {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Test
    void locDeLuyenTapVoiMoiToHopBoLocDeuChayDuoc() {
        Pageable firstPage = PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "examId"));

        // Đây là lời gọi của trang luyện tập lúc học sinh chưa chọn gì —
        // trường hợp hay gặp nhất và cũng là trường hợp rủi ro nhất.
        assertNotNull(examRepository.findPracticeExams(null, null, firstPage));

        assertNotNull(examRepository.findPracticeExams(1, null, firstPage));
        assertNotNull(examRepository.findPracticeExams(null, 1, firstPage));
        assertNotNull(examRepository.findPracticeExams(1, 1, firstPage));
    }

    /**
     * Câu {@code count} viết tay phải chạy được, không chỉ câu chính.
     *
     * Nó là một câu truy vấn riêng và chỉ được thực thi khi lấy Page — viết sai
     * thì cả trang luyện tập hỏng, dù câu chính hoàn toàn đúng.
     */
    @Test
    void demTongSoDeLuyenTapChayDuoc() {
        Page<?> page = examRepository.findPracticeExams(null, null, PageRequest.of(0, 5));
        assertTrue(page.getTotalElements() >= 0);
        assertTrue(page.getTotalPages() >= 0);
    }

    /** Xin một trang vượt quá số trang thực có thì trả về trang rỗng, không lỗi. */
    @Test
    void trangVuotQuaGioiHanTraVeRong() {
        assertNotNull(examRepository.findPracticeExams(null, null, PageRequest.of(999, 12)));
    }

    @Test
    void cacTruyVanTheoDanhSachIdDeuChayDuoc() {
        assertNotNull(examRepository.findByClassIdIn(List.of(1)));
        assertNotNull(examRepository.findPracticeExamsByLevelIdIn(List.of(1, 2)));
        assertNotNull(classRepository.findAllByIdWithDetails(List.of(1)));
        assertNotNull(classStudentRepository.countByClassIdIn(List.of(1)));
    }

    @Test
    void boLocTrinhDoCuaTrangLuyenTapChayDuoc() {
        assertNotNull(examRepository.countPracticeExamsByLevel());
    }
}
