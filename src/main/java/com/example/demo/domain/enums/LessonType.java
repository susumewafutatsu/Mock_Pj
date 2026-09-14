package com.example.demo.domain.enums;

/** Loại nội dung của một bài học. */
public enum LessonType {

    /** 文法 — cấu trúc ngữ pháp. Loại dùng nhiều nhất, và loại thẻ lật bất lực. */
    GRAMMAR,

    /** 漢字 — chữ Hán: mặt chữ, âm đọc, bộ thủ, số nét, từ ghép. */
    KANJI,

    /** 語彙 — từ vựng theo chủ đề. Thường gắn kèm một bộ thẻ để ôn lại. */
    VOCAB,

    /** 読解 — bài đọc hiểu. */
    READING,

    /** 聴解 — bài nghe. */
    LISTENING
}
