package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một bộ thẻ — nhóm từ vựng / chữ Hán được học cùng nhau.
 *
 * {@code owner == null} nghĩa là bộ dựng sẵn của hệ thống, ai cũng học được.
 * Khác null là bộ do một người tự tạo.
 *
 * Cố ý KHÔNG khoá bộ thẻ vào một loại thẻ duy nhất: một bài học thật luôn gồm
 * mấy chữ Hán mới đi kèm mấy từ mới, nên {@link DeckItem} mang loại thẻ chứ
 * không phải bảng này.
 */
@Entity
@Table(name = "Decks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DeckID")
    private Integer deckId;

    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Column(name = "Description", length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelID")
    private SubjectLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OwnerID")
    private User owner;

    @Column(name = "IsPublic", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    /** Bộ dựng sẵn của hệ thống, không thuộc về người dùng nào. */
    public boolean isSystemDeck() {
        return owner == null;
    }
}
