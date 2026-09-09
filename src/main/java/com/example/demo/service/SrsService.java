package com.example.demo.service;

import com.example.demo.domain.enums.StudyItemType;
import com.example.demo.dto.request.ReviewGradeRequest;
import com.example.demo.dto.response.DeckResponse;
import com.example.demo.dto.response.ReviewQueueResponse;
import com.example.demo.dto.response.ReviewResultResponse;

import java.util.List;

/**
 * Học thuộc từ vựng và chữ Hán bằng lặp lại ngắt quãng.
 *
 * Đây là nghiệp vụ HỌC, không đi qua đề thi hay phiên làm bài nào cả: người
 * học mở app mỗi ngày, ôn những thẻ đã tới hạn, và tự đánh giá mình nhớ tới
 * đâu. Lịch ôn của từng thẻ do {@link com.example.demo.service.srs.Sm2Scheduler}
 * tính.
 *
 * Vì sao phải là lặp lại ngắt quãng chứ không phải một danh sách từ để đọc
 * lại: một người thi N5 phải thuộc khoảng 800 từ, N1 là 10.000. Đọc lại toàn
 * bộ danh sách mỗi ngày là bất khả thi, nên phải có thứ chọn ra đúng vài chục
 * thẻ sắp quên của hôm nay.
 */
public interface SrsService {

    /** Các bộ thẻ người học xem được, kèm tiến độ của chính họ trên từng bộ. */
    List<DeckResponse> listDecks(String studentEmail);

    /**
     * Ghi danh học một bộ thẻ: tạo trạng thái ôn tập cho những thẻ chưa học.
     *
     * Idempotent, và quan trọng hơn là KHÔNG đặt lại tiến độ: thẻ đã học ở bộ
     * khác thì giữ nguyên lịch cũ. Hai bộ thẻ chồng lấn nhau là chuyện bình
     * thường (bộ "Bài 1" và bộ "Tổng hợp" dùng chung mấy từ), và học lại từ
     * đầu một thẻ đã thuộc là lãng phí đúng thứ mà thuật toán sinh ra để tiết kiệm.
     *
     * @return số thẻ mới được thêm vào lịch học
     */
    int enrollDeck(Integer deckId, String studentEmail);

    /**
     * Hàng đợi ôn của hôm nay.
     *
     * @param limit số thẻ tối đa, null thì lấy hạn mức mặc định của hệ thống
     */
    ReviewQueueResponse getDueQueue(String studentEmail, Integer limit);

    /** Ghi nhận người học vừa tự đánh giá một thẻ, trả về lịch mới của thẻ đó. */
    ReviewResultResponse review(StudyItemType itemType, Integer itemId,
                                ReviewGradeRequest request, String studentEmail);
}
