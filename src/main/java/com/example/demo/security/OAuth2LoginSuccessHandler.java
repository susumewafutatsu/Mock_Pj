package com.example.demo.security;

import com.example.demo.domain.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Google xác thực thành công → phát JWT của hệ thống rồi redirect về frontend
 * kèm token trong query string: {redirectUri}?accessToken=...&refreshToken=...&role=...
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Value("${app.oauth2.default-redirect-uri}")
    private String defaultRedirectUri;

    @Value("#{'${app.oauth2.authorized-redirect-uris}'.split(',')}")
    private List<String> authorizedRedirectUris;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            logger.debug("Response đã commit, không thể redirect tới " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request);
        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) {
        String redirectUri = CookieUtils
                .getCookie(request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME)
                .map(cookie -> cookie.getValue())
                .filter(this::isAuthorizedRedirectUri)
                .orElse(defaultRedirectUri);

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy user sau khi đăng nhập Google: " + email));

        String accessToken = jwtUtils.generateToken(user.getEmail(), "ROLE_" + user.getRole().name());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("role", user.getRole().name())
                .build()
                .toUriString();
    }

    /** Chỉ redirect tới các host/port đã khai báo trong application.yml để tránh open redirect. */
    private boolean isAuthorizedRedirectUri(String uri) {
        URI candidate = URI.create(uri);
        return authorizedRedirectUris.stream()
                .map(String::trim)
                .map(URI::create)
                .anyMatch(authorized -> authorized.getHost().equalsIgnoreCase(candidate.getHost())
                        && authorized.getPort() == candidate.getPort());
    }
}
