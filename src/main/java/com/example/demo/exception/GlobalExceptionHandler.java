package com.example.demo.exception;

import com.example.demo.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/** Một chỗ duy nhất dịch mọi ngoại lệ thành câu trả lời cho client. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<ApiResponse<Void>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }

    // ── Ngoại lệ nghiệp vụ của dự án ────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(UnauthorizedException e) {
        return body(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(BusinessException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    // ── Nhập liệu sai ───────────────────────────────────────────────────────

    /** Lỗi @Valid trên body. Ghép đúng những câu tiếng Việt đã viết trên annotation thay vì câu tóm tắt của framework. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST,
                message.isBlank() ? "Dữ liệu gửi lên không hợp lệ" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> invalidParam(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST,
                message.isBlank() ? "Tham số không hợp lệ" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException e) {
        // Không trả nguyên văn e.getMessage(): nó chứa tên lớp Java và vị trí ký tự.
        log.debug("Body không đọc được: {}", e.getMessage());
        return body(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đọc được");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST,
                "Giá trị của tham số \"" + e.getName() + "\" không đúng kiểu");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> missingParam(MissingServletRequestParameterException e) {
        return body(HttpStatus.BAD_REQUEST, "Thiếu tham số \"" + e.getParameterName() + "\"");
    }

    // ── Xác thực & phân quyền ───────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> unauthenticated(AuthenticationException e) {
        log.debug("Xác thực thất bại: {}", e.getMessage());
        return body(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ. Hãy đăng nhập lại.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN,
                "Tài khoản đang đăng nhập không có quyền dùng chức năng này.");
    }

    // ── Còn lại ─────────────────────────────────────────────────────────────

    /** Gọi sai đường dẫn. Spring Boot 3 ném cái này thay cho 404 mặc định cũ. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> noRoute(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn: " + e.getResourcePath());
    }

    /** Lưới cuối. Ghi log đầy đủ để còn sửa, nhưng trả ra ngoài một câu chung. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("Lỗi không lường trước", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "Hệ thống gặp lỗi khi xử lý yêu cầu. Vui lòng thử lại.");
    }
}
