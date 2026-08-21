package com.example.demo.domain.model;

import com.example.demo.domain.enums.AuthProvider;
import com.example.demo.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "UserID", length = 50)
    private String userId;

    @Column(name = "full_name", nullable = false, length = 100)  // ← ĐỔI THÀNH full_name
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 100)  // ← ĐỔI THÀNH email
    private String email;

    @Column(name = "password_hash", length = 255)  // ← ĐỔI THÀNH password_hash
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)  // ← ĐỔI THÀNH role
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", length = 20)  // ← ĐỔI THÀNH auth_provider
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "external_id", length = 100)  // ← ĐỔI THÀNH external_id
    private String externalId;

    @Column(name = "avatar_url", length = 255)  // ← ĐỔI THÀNH avatar_url
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)  // ← ĐỔI THÀNH created_at
    private LocalDateTime createdAt;
}