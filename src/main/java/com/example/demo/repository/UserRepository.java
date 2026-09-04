package com.example.demo.repository;

import com.example.demo.domain.enums.AuthProvider; 
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider);
    
    boolean existsByEmail(String email);

    /** Tìm student theo email — dùng khi giáo viên thêm học sinh vào lớp */
    Optional<User> findByEmailAndRole(String email, Role role);

    /** Danh sách tất cả student — dùng để gợi ý khi tìm kiếm */
    List<User> findByRole(Role role);
}