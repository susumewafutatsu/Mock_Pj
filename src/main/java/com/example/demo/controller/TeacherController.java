package com.example.demo.controller;

import com.example.demo.dto.request.ClassCreateRequest;
import com.example.demo.dto.request.ClassUpdateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.ClassResponse;
import com.example.demo.dto.response.ClassStudentResponse;
import com.example.demo.security.JwtUtils;
import com.example.demo.service.ClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API quản lý lớp học dành cho Giáo viên.
 *
 * Tất cả endpoints yêu cầu JWT hợp lệ với role = TEACHER.
 * Email giáo viên được extract từ token để xác định quyền sở hữu lớp.
 *
 * Base path: /api/teacher/classes
 */
@RestController
@RequestMapping("/api/teacher/classes")
@RequiredArgsConstructor
public class TeacherController {

    private final ClassService classService;
    private final JwtUtils jwtUtils;

    // ─── Helper ───────────────────────────────────────────────────

    private String extractEmail(String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        return jwtUtils.extractUsername(token);
    }

    // ─── Class CRUD ───────────────────────────────────────────────

    /**
     * GET /api/teacher/classes
     * Lấy danh sách lớp học của giáo viên đang đăng nhập.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ClassResponse>>> getMyClasses(
            @RequestHeader("Authorization") String authHeader) {
        try {
            String email = extractEmail(authHeader);
            List<ClassResponse> classes = classService.getMyClasses(email);
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lớp thành công", classes));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/teacher/classes
     * Tạo lớp học mới.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ClassResponse>> createClass(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ClassCreateRequest request) {
        try {
            String email = extractEmail(authHeader);
            ClassResponse created = classService.createClass(email, request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Tạo lớp học thành công", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * PUT /api/teacher/classes/{classId}
     * Cập nhật thông tin lớp học.
     */
    @PutMapping("/{classId}")
    public ResponseEntity<ApiResponse<ClassResponse>> updateClass(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer classId,
            @Valid @RequestBody ClassUpdateRequest request) {
        try {
            String email = extractEmail(authHeader);
            ClassResponse updated = classService.updateClass(email, classId, request);
            return ResponseEntity.ok(ApiResponse.success("Cập nhật lớp học thành công", updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * DELETE /api/teacher/classes/{classId}
     * Xóa lớp học (xóa cả danh sách học sinh đăng ký).
     */
    @DeleteMapping("/{classId}")
    public ResponseEntity<ApiResponse<Void>> deleteClass(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer classId) {
        try {
            String email = extractEmail(authHeader);
            classService.deleteClass(email, classId);
            return ResponseEntity.ok(ApiResponse.success("Xóa lớp học thành công", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ─── Student management ───────────────────────────────────────

    /**
     * GET /api/teacher/classes/{classId}/students
     * Lấy danh sách học sinh trong lớp.
     */
    @GetMapping("/{classId}/students")
    public ResponseEntity<ApiResponse<List<ClassStudentResponse>>> getStudents(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer classId) {
        try {
            String email = extractEmail(authHeader);
            List<ClassStudentResponse> students = classService.getStudentsInClass(email, classId);
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách học sinh thành công", students));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/teacher/classes/{classId}/students
     * Thêm học sinh vào lớp bằng email.
     * Body: { "studentEmail": "hoc.sinh@example.com" }
     */
    @PostMapping("/{classId}/students")
    public ResponseEntity<ApiResponse<Void>> addStudent(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer classId,
            @RequestBody Map<String, String> body) {
        try {
            String email = extractEmail(authHeader);
            String studentEmail = body.get("studentEmail");
            if (studentEmail == null || studentEmail.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("studentEmail không được để trống"));
            }
            classService.addStudentToClass(email, classId, studentEmail);
            return ResponseEntity.ok(ApiResponse.success("Thêm học sinh vào lớp thành công", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * DELETE /api/teacher/classes/{classId}/students/{studentId}
     * Xóa học sinh khỏi lớp.
     */
    @DeleteMapping("/{classId}/students/{studentId}")
    public ResponseEntity<ApiResponse<Void>> removeStudent(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer classId,
            @PathVariable String studentId) {
        try {
            String email = extractEmail(authHeader);
            classService.removeStudentFromClass(email, classId, studentId);
            return ResponseEntity.ok(ApiResponse.success("Xóa học sinh khỏi lớp thành công", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
