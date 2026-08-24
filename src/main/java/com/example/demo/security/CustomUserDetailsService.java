package com.example.demo.security;

import com.example.demo.domain.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // User đăng nhập bằng Google không có password_hash. Không thể throw ở đây vì
        // JwtAuthenticationFilter cũng dùng service này để validate token của họ.
        // Thay vào đó trả về password rỗng — BCrypt sẽ không khớp với bất kỳ mật khẩu nào,
        // nên /api/auth/login (local) vẫn bị từ chối.
        String password = user.getPasswordHash() == null ? "" : user.getPasswordHash();

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                password,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}