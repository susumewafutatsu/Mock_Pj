package com.example.demo.domain.model;

import com.example.demo.domain.enums.StudyItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;

/** Khoá của {@link DeckItem}: một thẻ chỉ nằm một lần trong một bộ. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DeckItemKey implements Serializable {

    @Column(name = "DeckID")
    private Integer deckId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ItemType", length = 10)
    private StudyItemType itemType;

    @Column(name = "ItemID")
    private Integer itemId;
}
