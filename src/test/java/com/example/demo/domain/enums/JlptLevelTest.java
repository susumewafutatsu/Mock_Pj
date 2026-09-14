package com.example.demo.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Luật chấm JLPT theo cấp. */
class JlptLevelTest {

    @Test
    @DisplayName("Ngưỡng đỗ tổng: N5 80 · N4 90 · N3 95 · N2 90 · N1 100")
    void nguongDoTheoCap() {
        assertEquals(80, JlptLevel.N5.getPassTotal());
        assertEquals(90, JlptLevel.N4.getPassTotal());
        assertEquals(95, JlptLevel.N3.getPassTotal());
        assertEquals(90, JlptLevel.N2.getPassTotal());
        assertEquals(100, JlptLevel.N1.getPassTotal());
    }

    @Test
    @DisplayName("N1–N3 có ba nhóm 60 điểm, điểm liệt 19")
    void n3CoBaNhom() {
        assertEquals(List.of(JlptScoreGroup.LANGUAGE_KNOWLEDGE, JlptScoreGroup.READING, JlptScoreGroup.LISTENING),
                JlptLevel.N3.groups());
        for (JlptScoreGroup g : JlptLevel.N3.groups()) {
            assertEquals(60, JlptLevel.N3.maxScoreOf(g));
            assertEquals(19, JlptLevel.N3.minScoreOf(g));
        }
    }

    @Test
    @DisplayName("N4–N5 gộp ngôn ngữ + đọc thành một nhóm 120 (liệt 38), nghe 60 (liệt 19)")
    void n5GopNhom() {
        assertEquals(List.of(JlptScoreGroup.LANGUAGE_AND_READING, JlptScoreGroup.LISTENING),
                JlptLevel.N5.groups());
        assertEquals(120, JlptLevel.N5.maxScoreOf(JlptScoreGroup.LANGUAGE_AND_READING));
        assertEquals(38, JlptLevel.N5.minScoreOf(JlptScoreGroup.LANGUAGE_AND_READING));
        assertEquals(60, JlptLevel.N5.maxScoreOf(JlptScoreGroup.LISTENING));
    }

    @Test
    @DisplayName("Tổng thang điểm các nhóm luôn là 180 ở mọi cấp")
    void tongLuon180() {
        for (JlptLevel level : JlptLevel.values()) {
            int total = level.groups().stream().mapToInt(level::maxScoreOf).sum();
            assertEquals(180, total, level.name());
        }
    }

    @Test
    @DisplayName("Kỹ năng rơi đúng ô: ở N3 đọc hiểu đứng riêng, ở N5 gộp với ngôn ngữ")
    void kyNangVaoDungO() {
        assertEquals(JlptScoreGroup.READING, JlptLevel.N3.groupOf(JlptSkill.READING));
        assertEquals(JlptScoreGroup.LANGUAGE_KNOWLEDGE, JlptLevel.N3.groupOf(JlptSkill.GRAMMAR));
        assertEquals(JlptScoreGroup.LANGUAGE_KNOWLEDGE, JlptLevel.N3.groupOf(JlptSkill.VOCABULARY));
        assertEquals(JlptScoreGroup.LANGUAGE_AND_READING, JlptLevel.N5.groupOf(JlptSkill.READING));
        assertEquals(JlptScoreGroup.LISTENING, JlptLevel.N5.groupOf(JlptSkill.LISTENING));
    }

    @Test
    @DisplayName("Trình độ tự đặt không phải cấp JLPT → không chấm theo JLPT")
    void trinhDoTuDat() {
        assertEquals(JlptLevel.N4, JlptLevel.fromLevelName(" n4 "));
        assertNull(JlptLevel.fromLevelName("Sơ cấp 1"));
        assertNull(JlptLevel.fromLevelName(null));
    }
}
