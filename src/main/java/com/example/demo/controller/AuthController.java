package com.example.demo.controller;

import com.example.demo.dto.request.AuthRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.request.RefreshTokenRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.security.RefreshTokenCookie;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Đăng nhập / đăng ký / làm mới phiên. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request,
                                                           HttpServletResponse servletResponse) {
        try {
            AuthResponse response = authService.login(request);
            moveRefreshTokenToCookie(response, servletResponse);
            return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            UserResponse response = authService.register(request);
            return ResponseEntity.ok(ApiResponse.success("Đăng ký thành công", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /** Làm mới phiên. Ưu tiên token trong cookie; body chỉ còn để tương thích với client cũ chưa cập nhật. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        try {
            String token = refreshTokenCookie.read(servletRequest)
                    .orElse(body == null ? null : body.getRefreshToken());
            if (token == null || token.isBlank()) {
                throw new RuntimeException("Phiên đăng nhập đã hết. Hãy đăng nhập lại.");
            }
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken(token);

            AuthResponse response = authService.refreshToken(request);
            moveRefreshTokenToCookie(response, servletResponse);
            return ResponseEntity.ok(ApiResponse.success("Refresh token thành công", response));
        } catch (Exception e) {
            // Token hỏng hoặc hết hạn: xoá luôn cookie, khỏi để trình duyệt gửi đi
            // gửi lại một thứ không bao giờ dùng được nữa.
            refreshTokenCookie.clear(servletResponse);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    /** Đăng xuất: xoá cookie refresh. Access token phía client do client tự bỏ. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse servletResponse) {
        refreshTokenCookie.clear(servletResponse);
        return ResponseEntity.ok(ApiResponse.success("Đã đăng xuất", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            UserResponse response = authService.getCurrentUser(authHeader);
            return ResponseEntity.ok(ApiResponse.success("Lấy thông tin user thành công", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    private void moveRefreshTokenToCookie(AuthResponse response, HttpServletResponse servletResponse) {
        if (response.getRefreshToken() != null) {
            refreshTokenCookie.write(servletResponse, response.getRefreshToken());
            response.setRefreshToken(null);
        }
    }
}
