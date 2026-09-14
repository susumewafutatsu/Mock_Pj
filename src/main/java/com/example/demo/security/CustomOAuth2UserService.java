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

import java.util.Collections;
import java.util.Map;

/** Nhận thông tin user từ Google (userinfo endpoint) rồi tạo mới / cập nhật User trong DB. */
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
                // Tài khoản mới qua Google cũng LUÔN là học viên.
                .orElseGet(() -> createGoogleUser(email, fullName, avatarUrl, externalId));
        // Không thì tài khoản bị khoá chỉ cần bấm "Đăng nhập bằng Google" là vào lại được.
        if (user.isLocked()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_locked"),
                    "Tài khoản đã bị quản trị viên khoá");
        }
        user = userRepository.save(user);

        // Authority dùng đúng format "ROLE_x" như CustomUserDetailsService để phân quyền nhất quán
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes,
                "email");
    }

    /** Email đã tồn tại: liên kết thêm thông tin Google. */
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

    /** Lần đầu đăng nhập bằng Google: tạo tài khoản học viên. */
    private User createGoogleUser(String email, String fullName, String avatarUrl, String externalId) {
        return User.builder()
                .email(email)
                .fullName(fullName)
                .avatarUrl(avatarUrl)
                .externalId(externalId)
                .authProvider(AuthProvider.GOOGLE)
                .role(Role.STUDENT)
                .build();
    }
}
