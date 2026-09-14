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

/** Các câu truy vấn mới của màn hình chọn đề có THỰC SỰ chạy được trên MySQL không. */
@SpringBootTest
@Transactional(readOnly = true)
class StudentExamQueriesTest {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private RoomExamRepository roomExamRepository;

    @Test
    void locDeLuyenTapVoiMoiToHopBoLocDeuChayDuoc() {
        Pageable firstPage = PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "examId"));

        // Đây là lời gọi của trang luyện tập lúc thí sinh chưa chọn gì.
        assertNotNull(examRepository.findPracticeExams(null, null, firstPage));

        assertNotNull(examRepository.findPracticeExams(1, null, firstPage));
        assertNotNull(examRepository.findPracticeExams(null, 1, firstPage));
        assertNotNull(examRepository.findPracticeExams(1, 1, firstPage));
    }

    /** Câu {@code count} viết tay phải chạy được, không chỉ câu chính. */
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
        // Sau khi bỏ lớp, mấy câu này join qua bảng nối RoomExams thay vì đọc một cột trên chính đề.
        assertNotNull(examRepository.findByRoomIdIn(List.of(1)));
        assertNotNull(examRepository.findPracticeExamsByLevelIdIn(List.of(1, 2)));
        assertNotNull(roomRepository.findAllByIdWithDetails(List.of(1)));
        assertNotNull(roomMemberRepository.countActiveByRoomIdIn(List.of(1)));
        assertNotNull(roomExamRepository.countByRoomIdIn(List.of(1)));
        assertNotNull(roomExamRepository.findExamRoomPairs(List.of(1)));
    }

    @Test
    void boLocTrinhDoCuaTrangLuyenTapChayDuoc() {
        assertNotNull(examRepository.countPracticeExamsByLevel());
    }
}
