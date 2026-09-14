package com.example.demo.domain.enums;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Luật chấm của từng cấp JLPT. */
public enum JlptLevel {

    N5(80,  false),
    N4(90,  false),
    N3(95,  true),
    N2(90,  true),
    N1(100, true);

    /** Điểm liệt của một nhóm 60 điểm. */
    public static final int SECTION_MIN_60 = 19;

    /** Điểm liệt của nhóm gộp 120 điểm ở N4/N5. */
    public static final int SECTION_MIN_120 = 38;

    public static final int TOTAL_MAX = 180;

    private final int passTotal;

    /** true = ba nhóm (N1–N3); false = hai nhóm, ngôn ngữ và đọc gộp chung (N4–N5). */
    private final boolean threeGroups;

    JlptLevel(int passTotal, boolean threeGroups) {
        this.passTotal = passTotal;
        this.threeGroups = threeGroups;
    }

    /** Tổng điểm tối thiểu để đỗ cấp này. */
    public int getPassTotal() {
        return passTotal;
    }

    public boolean isThreeGroups() {
        return threeGroups;
    }

    /** Tìm luật chấm theo tên trình độ trong CSDL ("N5"… "N1"). */
    public static JlptLevel fromLevelName(String levelName) {
        if (levelName == null) {
            return null;
        }
        String key = levelName.trim().toUpperCase();
        for (JlptLevel level : values()) {
            if (level.name().equals(key)) {
                return level;
            }
        }
        return null;
    }

    /** Câu hỏi của kỹ năng này được cộng vào ô nào trên bảng điểm của cấp này. */
    public JlptScoreGroup groupOf(JlptSkill skill) {
        if (skill == JlptSkill.LISTENING) {
            return JlptScoreGroup.LISTENING;
        }
        if (!threeGroups) {
            // N4/N5: từ vựng, ngữ pháp và đọc hiểu chung một ô 120 điểm.
            return JlptScoreGroup.LANGUAGE_AND_READING;
        }
        return skill == JlptSkill.READING
                ? JlptScoreGroup.READING
                : JlptScoreGroup.LANGUAGE_KNOWLEDGE;
    }

    /** Các ô trên bảng điểm của cấp này, theo đúng thứ tự hiển thị. */
    public List<JlptScoreGroup> groups() {
        return threeGroups
                ? List.of(JlptScoreGroup.LANGUAGE_KNOWLEDGE, JlptScoreGroup.READING, JlptScoreGroup.LISTENING)
                : List.of(JlptScoreGroup.LANGUAGE_AND_READING, JlptScoreGroup.LISTENING);
    }

    /** Điểm tối đa của một ô. */
    public int maxScoreOf(JlptScoreGroup group) {
        return group == JlptScoreGroup.LANGUAGE_AND_READING ? 120 : 60;
    }

    /** Điểm liệt của một ô — dưới mức này là trượt, kể cả khi tổng đã đủ. */
    public int minScoreOf(JlptScoreGroup group) {
        return group == JlptScoreGroup.LANGUAGE_AND_READING ? SECTION_MIN_120 : SECTION_MIN_60;
    }

    /** Bảng "ô → điểm tối đa" để dựng kết quả, giữ nguyên thứ tự hiển thị. */
    public Map<JlptScoreGroup, Integer> maxScoreByGroup() {
        Map<JlptScoreGroup, Integer> map = new LinkedHashMap<>();
        for (JlptScoreGroup group : groups()) {
            map.put(group, maxScoreOf(group));
        }
        return map;
    }
}
