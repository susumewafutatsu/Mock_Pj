// src/main/java/com/example/demo/service/AuthService.java
package com.example.demo.service;

import com.example.demo.domain.enums.Role;
import com.example.demo.dto.request.AuthRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.request.RefreshTokenRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.domain.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import com.example.demo.domain.enums.AuthProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String LOCKED_MESSAGE =
        "Tài khoản đã bị quản trị viên khoá. Liên hệ quản trị viên để được mở lại.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    
    @Transactional
    public AuthResponse login(AuthRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (LockedException e) {
            // Câu mặc định của Spring là tiếng Anh ("User account is locked").
            throw new RuntimeException(LOCKED_MESSAGE);
        } catch (BadCredentialsException e) {
            // Một câu duy nhất cho cả sai email lẫn sai mật khẩu.
            throw new RuntimeException("Email hoặc mật khẩu không đúng.");
        }
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        User user = userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        String accessToken = jwtUtils.generateToken(userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails.getUsername());
        
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .user(mapToUserResponse(user))
            .build();
    }
    
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email này đã được đăng ký.");
        }
        
        // MỌI tài khoản đăng ký đều là HỌC VIÊN.
        User user = User.builder()
            .fullName(request.getFullName())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(Role.STUDENT)
            .authProvider(AuthProvider.LOCAL)
            .createdAt(LocalDateTime.now())
            .build();
        
        user = userRepository.save(user);
        return mapToUserResponse(user);
    }
    
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        String email = jwtUtils.extractUsername(refreshToken);
        
        if (email == null) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isAccountNonLocked()) {
            throw new RuntimeException(LOCKED_MESSAGE);
        }
        if (!jwtUtils.validateToken(refreshToken, userDetails)) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        String newAccessToken = jwtUtils.generateToken(userDetails);
        String newRefreshToken = jwtUtils.generateRefreshToken(email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .user(mapToUserResponse(user))
            .build();
    }
    
    public UserResponse getCurrentUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid authorization header");
        }
        
        String token = authHeader.substring(7);
        String email = jwtUtils.extractUsername(token);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        // /api/auth/** là permitAll nên request này không qua JwtAuthenticationFilter.
        if (user.isLocked()) {
            throw new RuntimeException(LOCKED_MESSAGE);
        }

        return mapToUserResponse(user);
    }
    
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
            .userId(user.getUserId())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .role(user.getRole().name()) // Role enum -> String
            .avatarUrl(user.getAvatarUrl())
            .createdAt(user.getCreatedAt())
            .build();
    }
}