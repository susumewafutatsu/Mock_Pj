package com.example.demo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/** Google xác thực thất bại → redirect về trang login của frontend kèm thông báo lỗi. */
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Value("${app.oauth2.login-failure-uri}")
    private String loginFailureUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String targetUrl = UriComponentsBuilder.fromUriString(loginFailureUri)
                .queryParam("error", exception.getLocalizedMessage())
                .build()
                .toUriString();

        authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
