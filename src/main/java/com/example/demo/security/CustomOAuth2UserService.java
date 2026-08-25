package com.example.demo.security;

import com.example.demo.domain.enums.AuthProvider;
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;

import java.util.Collections;
import java.util.Map;

/**
 * Nhận thông tin user từ Google (userinfo endpoint) rồi tạo mới / cập nhật User trong DB.
 * Được gọi bởi Spring Security trong luồng oauth2Login (xem SecurityConfig).
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"),
                    "Tài khoản Google không cung cấp email");
        }

        String fullName = (String) attributes.getOrDefault("name", email);
        String avatarUrl = (String) attributes.get("picture");
        String externalId = (String) attributes.get("sub");

        User user = userRepository.findByEmail(email)
                .map(existing -> linkGoogleAccount(existing, fullName, avatarUrl, externalId))
                .orElseGet(() -> {
                    // Mặc định là STUDENT, lấy từ cookie nếu có
                    Role defaultRole = Role.STUDENT;
                    ServletRequestAttributes attributesRequest = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributesRequest != null) {
                        HttpServletRequest request = attributesRequest.getRequest();
                        defaultRole = CookieUtils.getCookie(request, HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_ROLE_PARAM_COOKIE_NAME)
                                .map(Cookie::getValue)
                                .map(val -> {
                                    try {
                                        return Role.valueOf(val.toUpperCase());
                                    } catch (IllegalArgumentException e) {
                                        return Role.STUDENT;
                                    }
                                })
                                .orElse(Role.STUDENT);
                    }
                    return createGoogleUser(email, fullName, avatarUrl, externalId, defaultRole);
                });
        user = userRepository.save(user);

        // Authority dùng đúng format "ROLE_x" như CustomUserDetailsService để phân quyền nhất quán
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes,
                "email");
    }

    /**
     * Email đã tồn tại: liên kết thêm thông tin Google, giữ nguyên role và authProvider gốc
     * (user đăng ký LOCAL vẫn đăng nhập được bằng mật khẩu).
     */
    private User linkGoogleAccount(User user, String fullName, String avatarUrl, String externalId) {
        if (user.getExternalId() == null) {
            user.setExternalId(externalId);
        }
        if (user.getAvatarUrl() == null) {
            user.setAvatarUrl(avatarUrl);
        }
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(fullName);
        }
        if (user.getAuthProvider() == null) {
            user.setAuthProvider(AuthProvider.GOOGLE);
        }
        return user;
    }

    /** Lần đầu đăng nhập bằng Google: tạo user mới với role lấy từ cookie hoặc mặc định STUDENT. */
    private User createGoogleUser(String email, String fullName, String avatarUrl, String externalId, Role role) {
        return User.builder()
                .email(email)
                .fullName(fullName)
                .avatarUrl(avatarUrl)
                .externalId(externalId)
                .authProvider(AuthProvider.GOOGLE)
                .role(role)
                .build();
    }
}
