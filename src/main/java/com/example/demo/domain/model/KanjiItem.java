package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một chữ Hán trong kho nội dung học.
 *
 * Tên cột {@code Glyph} thay vì {@code Character}: CHARACTER là từ khoá dành
 * riêng của MySQL, đặt tên cột như vậy là phải quote ở mọi câu lệnh về sau.
 */
@Entity
@Table(name = "KanjiItems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KanjiItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "KanjiID")
    private Integer kanjiId;

    /**
     * Bản thân chữ Hán. Để 8 ký tự chứ không phải 1 vì vài chữ nằm ngoài mặt
     * phẳng cơ bản của Unicode chiếm nhiều hơn một đơn vị mã.
     */
    @Column(name = "Glyph", nullable = false, unique = true, length = 8)
    private String glyph;

    /** 音読み — âm Hán Nhật, ghi bằng katakana theo quy ước từ điển. */
    @Column(name = "Onyomi", length = 100)
    private String onyomi;

    /** 訓読み — âm thuần Nhật, ghi bằng hiragana. */
    @Column(name = "Kunyomi", length = 100)
    private String kunyomi;

    @Column(name = "Meaning", nullable = false, length = 255)
    private String meaning;

    @Column(name = "StrokeCount")
    private Integer strokeCount;

    @Column(name = "Radical", length = 8)
    private String radical;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelID")
    private SubjectLevel level;

    /** Mẹo nhớ mặt chữ. Không bắt buộc nhưng là thứ khiến chữ Hán bớt vô nghĩa. */
    @Column(name = "Mnemonic", length = 500)
    private String mnemonic;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
