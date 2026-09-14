package com.example.demo.domain.enums;

/** Kỹ năng JLPT của một câu hỏi — quyết định câu đó được cộng vào nhóm điểm nào. */
public enum JlptSkill {

    /** 文字・語彙 — chữ Hán và từ vựng. */
    VOCABULARY("文字・語彙", "Chữ Hán · Từ vựng"),

    /** 文法 — ngữ pháp. */
    GRAMMAR("文法", "Ngữ pháp"),

    /** 読解 — đọc hiểu. */
    READING("読解", "Đọc hiểu"),

    /** 聴解 — nghe hiểu. */
    LISTENING("聴解", "Nghe hiểu");

    private final String japaneseName;
    private final String vietnameseName;

    JlptSkill(String japaneseName, String vietnameseName) {
        this.japaneseName = japaneseName;
        this.vietnameseName = vietnameseName;
    }

    public String getJapaneseName() {
        return japaneseName;
    }

    public String getVietnameseName() {
        return vietnameseName;
    }
}
