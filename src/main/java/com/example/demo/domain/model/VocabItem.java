package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Một từ vựng tiếng Nhật trong kho nội dung học.
 *
 * Đây là nội dung dùng chung, không thuộc về người học nào. Việc "tôi thuộc từ
 * này tới đâu" nằm ở {@link UserCardState}.
 */
@Entity
@Table(
        name = "VocabItems",
        uniqueConstraints = @UniqueConstraint(
                name = "UC_Vocab_Word_Reading",
                columnNames = {"Word", "Reading"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocabItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VocabID")
    private Integer vocabId;

    /** Dạng viết thường gặp — có kanji (食べる) hoặc thuần kana (たべる). */
    @Column(name = "Word", nullable = false, length = 100)
    private String word;

    /**
     * Cách đọc bằng kana. Luôn bắt buộc, kể cả khi {@link #word} đã là kana:
     * màn ôn thẻ hiển thị furigana từ cột này, và phần so đáp án gõ tay cũng
     * so với cột này chứ không so với chữ viết.
     */
    @Column(name = "Reading", nullable = false, length = 100)
    private String reading;

    @Column(name = "Meaning", nullable = false, length = 255)
    private String meaning;

    @Column(name = "PartOfSpeech", length = 30)
    private String partOfSpeech;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LevelID")
    private SubjectLevel level;

    @Column(name = "ExampleSentence", length = 500)
    private String exampleSentence;

    @Column(name = "ExampleMeaning", length = 500)
    private String exampleMeaning;

    @Column(name = "AudioURL", length = 255)
    private String audioUrl;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;
}
