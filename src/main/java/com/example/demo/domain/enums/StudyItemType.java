package com.example.demo.domain.enums;

/**
 * Loại thẻ học. Dùng chung cho DeckItems và UserCardStates.
 *
 * Hai loại thẻ nằm ở hai bảng riêng ({@code VocabItems} / {@code KanjiItems})
 * vì chúng có những trường hoàn toàn khác nhau — chữ Hán có số nét và bộ thủ,
 * từ vựng có từ loại và câu ví dụ. Gộp vào một bảng thì quá nửa số cột luôn
 * rỗng. Cái giá phải trả là {@code ItemID} trở thành tham chiếu đa hình, không
 * có khoá ngoại, nên enum này chính là thứ cho biết phải tra bảng nào.
 */
public enum StudyItemType {
    VOCAB,
    KANJI
}
