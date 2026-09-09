package com.example.demo.controller;

import com.example.demo.dto.request.CourseCreateRequest;
import com.example.demo.dto.request.CourseReviewRequest;
import com.example.demo.dto.request.LessonCreateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.CourseDetailResponse;
import com.example.demo.dto.response.CourseResponse;
import com.example.demo.dto.response.LessonDetailResponse;
import com.example.demo.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Khoá học — nơi chứa ngữ pháp và chữ Hán.
 *
 * Đặt ở {@code /api/courses} chứ không dưới {@code /api/teacher} hay
 * {@code /api/student}, cùng lý do với {@link RoomController}: đây là tài
 * nguyên mà ba vai cùng chạm vào, chỉ khác góc nhìn. Tách theo vai sẽ sinh ra
 * ba bộ endpoint gần giống hệt nhau cho cùng một thứ.
 *
 * Nhánh này chỉ đòi "đã đăng nhập"; việc phân quyền nằm ở tầng service —
 * soạn khoá đòi vai người ra đề, duyệt đòi vai Admin, và mọi thao tác trên một
 * khoá cụ thể đều kiểm quyền sở hữu. Người không được xem nhận 404 chứ không
 * phải 403, để không lộ ra khoá đó có tồn tại.
 *
 * Người ra đề:
 *   GET    /courses/mine                          -> khoá tôi soạn, mọi trạng thái
 *   POST   /courses                               -> tạo khoá (luôn bắt đầu ở DRAFT)
 *   PUT    /courses/{id}                          -> sửa thông tin khoá
 *   DELETE /courses/{id}                          -> xoá (chỉ khi chưa ai ghi danh)
 *   POST   /courses/{id}/submit                   -> gửi duyệt
 *   POST   /courses/{id}/lessons                  -> thêm bài
 *   PUT    /courses/{id}/lessons/{lessonId}       -> sửa bài
 *   DELETE /courses/{id}/lessons/{lessonId}       -> xoá bài
 *
 * Admin:
 *   GET    /courses/pending                       -> hàng đợi duyệt
 *   POST   /courses/{id}/approve                  -> duyệt và xuất bản
 *   POST   /courses/{id}/reject                   -> từ chối, bắt buộc kèm lý do
 *
 * Thí sinh:
 *   GET    /courses?levelId=                      -> khoá đã xuất bản
 *   GET    /courses/enrolled                      -> khoá tôi đang theo, kèm phần trăm
 *   GET    /courses/{id}                          -> chi tiết + danh sách bài
 *   POST   /courses/{id}/enroll                   -> ghi danh
 *   GET    /courses/{id}/lessons/{lessonId}       -> đọc lý thuyết
 *   POST   /courses/{id}/lessons/{lessonId}/complete -> đánh dấu đã học xong
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    // ── Người ra đề ─────────────────────────────────────────────────────────

    @GetMapping("/mine")
    public ApiResponse<List<CourseResponse>> myCourses(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(courseService.getMyCourses(me.getUsername()));
    }

    @PostMapping
    public ApiResponse<CourseResponse> create(@Valid @RequestBody CourseCreateRequest request,
                                              @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã tạo khoá học ở dạng nháp",
                courseService.createCourse(me.getUsername(), request));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> update(@PathVariable Integer courseId,
                                              @Valid @RequestBody CourseCreateRequest request,
                                              @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã cập nhật khoá học",
                courseService.updateCourse(me.getUsername(), courseId, request));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> delete(@PathVariable Integer courseId,
                                    @AuthenticationPrincipal UserDetails me) {
        courseService.deleteCourse(me.getUsername(), courseId);
        return ApiResponse.success("Đã xoá khoá học", null);
    }

    /** Gửi duyệt. Khoá chưa có bài nào thì không gửi được. */
    @PostMapping("/{courseId}/submit")
    public ApiResponse<CourseResponse> submit(@PathVariable Integer courseId,
                                              @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã gửi duyệt, chờ quản trị viên xem xét",
                courseService.submitForReview(me.getUsername(), courseId));
    }

    @PostMapping("/{courseId}/lessons")
    public ApiResponse<CourseDetailResponse> addLesson(
            @PathVariable Integer courseId,
            @Valid @RequestBody LessonCreateRequest request,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã thêm bài học",
                courseService.addLesson(me.getUsername(), courseId, request));
    }

    @PutMapping("/{courseId}/lessons/{lessonId}")
    public ApiResponse<CourseDetailResponse> updateLesson(
            @PathVariable Integer courseId,
            @PathVariable Integer lessonId,
            @Valid @RequestBody LessonCreateRequest request,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã cập nhật bài học",
                courseService.updateLesson(me.getUsername(), courseId, lessonId, request));
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}")
    public ApiResponse<CourseDetailResponse> deleteLesson(
            @PathVariable Integer courseId,
            @PathVariable Integer lessonId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã xoá bài học",
                courseService.deleteLesson(me.getUsername(), courseId, lessonId));
    }

    // ── Admin ───────────────────────────────────────────────────────────────

    /** Hàng đợi duyệt. Khoá chờ lâu nhất lên đầu. */
    @GetMapping("/pending")
    public ApiResponse<List<CourseResponse>> pending(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(courseService.getPendingCourses(me.getUsername()));
    }

    @PostMapping("/{courseId}/approve")
    public ApiResponse<CourseResponse> approve(@PathVariable Integer courseId,
                                               @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã duyệt và xuất bản khoá học",
                courseService.approve(me.getUsername(), courseId));
    }

    /** Từ chối. Bắt buộc kèm lý do — không có thì tác giả không biết sửa gì. */
    @PostMapping("/{courseId}/reject")
    public ApiResponse<CourseResponse> reject(
            @PathVariable Integer courseId,
            @RequestBody(required = false) CourseReviewRequest request,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã trả lại khoá học cho tác giả",
                courseService.reject(me.getUsername(), courseId, request));
    }

    // ── Thí sinh ────────────────────────────────────────────────────────────

    @GetMapping
    public ApiResponse<List<CourseResponse>> browse(
            @RequestParam(required = false) Integer levelId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(courseService.browsePublished(me.getUsername(), levelId));
    }

    @GetMapping("/enrolled")
    public ApiResponse<List<CourseResponse>> enrolled(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(courseService.getEnrolledCourses(me.getUsername()));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> detail(@PathVariable Integer courseId,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(courseService.getCourse(me.getUsername(), courseId));
    }

    @PostMapping("/{courseId}/enroll")
    public ApiResponse<CourseResponse> enroll(@PathVariable Integer courseId,
                                              @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã ghi danh khoá học",
                courseService.enroll(me.getUsername(), courseId));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}")
    public ApiResponse<LessonDetailResponse> lesson(@PathVariable Integer courseId,
                                                    @PathVariable Integer lessonId,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(courseService.getLesson(me.getUsername(), courseId, lessonId));
    }

    /**
     * Đánh dấu đã đọc xong. Trả về khoá kèm phần trăm mới để màn hình cập nhật
     * ngay, khỏi phải gọi thêm một lượt nữa.
     */
    @PostMapping("/{courseId}/lessons/{lessonId}/complete")
    public ApiResponse<CourseResponse> complete(@PathVariable Integer courseId,
                                                @PathVariable Integer lessonId,
                                                @AuthenticationPrincipal UserDetails me) {
        CourseResponse course =
                courseService.completeLesson(me.getUsername(), courseId, lessonId);
        return ApiResponse.success(
                course.getProgressPercent() == 100
                        ? "Hoàn thành khoá học 🎉"
                        : "Đã học xong bài này — " + course.getProgressPercent() + "%",
                course);
    }
}
