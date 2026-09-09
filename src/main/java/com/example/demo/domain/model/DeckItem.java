package com.example.demo.domain.model;

import com.example.demo.domain.enums.StudyItemType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Một thẻ nằm trong một bộ.
 *
 * {@code id.itemId} là tham chiếu đa hình: trỏ tới {@code VocabItems} hay
 * {@code KanjiItems} là tuỳ {@code id.itemType}. Không có khoá ngoại — đó là
 * cái giá để một bộ chứa được cả hai loại thẻ. Việc kiểm tra thẻ có thật hay
 * không do tầng service làm lúc thêm thẻ vào bộ.
 */
@Entity
@Table(name = "DeckItems")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeckItem {

    @EmbeddedId
    private DeckItemKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("deckId")
    @JoinColumn(name = "DeckID")
    private Deck deck;

    /** Thứ tự hiển thị trong bộ. Thẻ mới học nên xếp theo đúng thứ tự bài. */
    @Column(name = "OrderNo", nullable = false)
    @Builder.Default
    private Integer orderNo = 1;

    public StudyItemType itemType() {
        return id == null ? null : id.getItemType();
    }

    public Integer itemId() {
        return id == null ? null : id.getItemId();
    }
}
