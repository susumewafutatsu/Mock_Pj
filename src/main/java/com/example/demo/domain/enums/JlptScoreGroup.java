package com.example.demo.domain.enums;

/** Nhóm điểm trên bảng kết quả JLPT. */
public enum JlptScoreGroup {

    /** 言語知識（文字・語彙・文法）— dùng ở N1, N2, N3. */
    LANGUAGE_KNOWLEDGE("言語知識（文字・語彙・文法）", "Kiến thức ngôn ngữ"),

    /** 読解 — dùng ở N1, N2, N3. */
    READING("読解", "Đọc hiểu"),

    /** 言語知識・読解 gộp chung — dùng ở N4, N5. */
    LANGUAGE_AND_READING("言語知識・読解", "Kiến thức ngôn ngữ · Đọc hiểu"),

    /** 聴解 — dùng ở mọi cấp. */
    LISTENING("聴解", "Nghe hiểu");

    private final String japaneseName;
    private final String vietnameseName;

    JlptScoreGroup(String japaneseName, String vietnameseName) {
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
