package com.example.demo.repository;

import com.example.demo.domain.enums.AuthProvider; 
import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider);
    
    boolean existsByEmail(String email);

    /** Tìm thí sinh theo email. */
    Optional<User> findByEmailAndRole(String email, Role role);

    /** Danh sách tất cả student — dùng để gợi ý khi tìm kiếm */
    List<User> findByRole(Role role);

    // ── Trang quản trị ──────────────────────────────────────────────────────

    /** Danh sách người dùng cho admin, mọi bộ lọc đều tuỳ chọn (truyền null là không lọc). */
    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:locked is null or u.locked = :locked)
              and (:q is null or lower(u.email) like :q or lower(u.fullName) like :q)
            """)
    Page<User> searchForAdmin(@Param("role") Role role,
                              @Param("locked") Boolean locked,
                              @Param("q") String q,
                              Pageable pageable);

    long countByRole(Role role);

    long countByLockedTrue();

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);
}