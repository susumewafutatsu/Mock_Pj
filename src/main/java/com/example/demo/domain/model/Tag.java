package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Tags", uniqueConstraints = {
    @UniqueConstraint(name = "UC_Tag_TagName", columnNames = {"TagName"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TagID")
    private Integer tagId;

    /** Luôn lưu dạng slug chữ thường (vd "dai-so") để so sánh không phụ thuộc hoa/thường. */
    @Column(name = "TagName", nullable = false, length = 50)
    private String tagName;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
