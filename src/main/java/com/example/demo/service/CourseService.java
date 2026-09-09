package com.example.demo.service;

import com.example.demo.dto.request.CourseCreateRequest;
import com.example.demo.dto.request.CourseReviewRequest;
import com.example.demo.dto.request.LessonCreateRequest;
import com.example.demo.dto.response.CourseDetailResponse;
import com.example.demo.dto.response.CourseResponse;
import com.example.demo.dto.response.LessonDetailResponse;

import java.util.List;

/**
 * Khoá học — nơi chứa NGỮ PHÁP và CHỮ HÁN, hai loại nội dung mà bộ thẻ ở
 * {@link SrsService} không diễn tả nổi.
 *
 * Một khoá là danh sách bài học có thứ tự; một bài là một khối lý thuyết để
 * đọc. Đọc xong bấm hoàn thành, tiến độ là số bài xong chia tổng số bài. Cố ý
 * dừng ở mức đó — không có khối nội dung ghép, không đo thời gian đọc, không có
 * trạng thái "đang đọc dở".
 *
 * Ba vai, ba nhóm phương thức:
 *
 *   - Người ra đề soạn nội dung và gửi duyệt.
 *   - Admin duyệt hoặc từ chối kèm lý do.
 *   - Thí sinh chỉ thấy khoá đã xuất bản, ghi danh và học.
 *
 * Vì sao tách vai soạn khỏi vai duyệt: thứ tự dạy ngữ pháp là kiến thức của
 * người dạy, nhưng lộ trình lại là thứ hàng nghìn người đi theo từ đầu tới cuối
 * — một bài xếp sai chỗ không hỏng một buổi học, nó hỏng cả quá trình của người
 * đi theo nó, mà thí sinh mới thì không có cách nào tự phát hiện.
 */
public interface CourseService {

    // ── Người ra đề ────────────────────────────────────────────────────────

    /** Khoá do người này soạn, mọi trạng thái. */
    List<CourseResponse> getMyCourses(String authorEmail);

    CourseResponse createCourse(String authorEmail, CourseCreateRequest request);

    CourseResponse updateCourse(String authorEmail, Integer courseId, CourseCreateRequest request);

    /** Xoá khoá. Chỉ được khi chưa có ai ghi danh. */
    void deleteCourse(String authorEmail, Integer courseId);

    /** Gửi duyệt: DRAFT / REJECTED → PENDING. Khoá rỗng thì không gửi được. */
    CourseResponse submitForReview(String authorEmail, Integer courseId);

    CourseDetailResponse addLesson(String authorEmail, Integer courseId,
                                   LessonCreateRequest request);

    CourseDetailResponse updateLesson(String authorEmail, Integer courseId,
                                      Integer lessonId, LessonCreateRequest request);

    CourseDetailResponse deleteLesson(String authorEmail, Integer courseId, Integer lessonId);

    // ── Admin ──────────────────────────────────────────────────────────────

    /** Hàng đợi duyệt, khoá chờ lâu nhất lên đầu. */
    List<CourseResponse> getPendingCourses(String adminEmail);

    CourseResponse approve(String adminEmail, Integer courseId);

    /** Từ chối. Bắt buộc có lý do — không có thì tác giả không biết sửa gì. */
    CourseResponse reject(String adminEmail, Integer courseId, CourseReviewRequest request);

    // ── Thí sinh ───────────────────────────────────────────────────────────

    /** Khoá đã xuất bản, lọc tuỳ chọn theo trình độ. */
    List<CourseResponse> browsePublished(String userEmail, Integer levelId);

    /** Khoá người này đang theo, kèm phần trăm. */
    List<CourseResponse> getEnrolledCourses(String userEmail);

    /** Chi tiết một khoá kèm danh sách bài và bài nào đã xong. */
    CourseDetailResponse getCourse(String userEmail, Integer courseId);

    /** Ghi danh. Idempotent — bấm lại không tạo dòng thứ hai. */
    CourseResponse enroll(String userEmail, Integer courseId);

    /** Nội dung một bài để đọc. */
    LessonDetailResponse getLesson(String userEmail, Integer courseId, Integer lessonId);

    /**
     * Đánh dấu đã đọc xong một bài.
     *
     * Idempotent, và cố ý không kiểm tra người học có thật sự đọc hết hay không:
     * đây là công cụ tự theo dõi tiến độ, không phải bài kiểm tra.
     *
     * @return khoá kèm phần trăm mới, để màn hình cập nhật ngay
     */
    CourseResponse completeLesson(String userEmail, Integer courseId, Integer lessonId);
}
