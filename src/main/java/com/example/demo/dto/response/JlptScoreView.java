package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Bảng điểm kiểu JLPT của một bài đã nộp. */
@Data
@Builder
public class JlptScoreView {

    /** "N3" — cấp của đề. */
    private String level;

    private List<Group> groups;

    private int totalScore;
    private int totalMax;

    /** Ngưỡng tổng của cấp này (N5 80 · N4 90 · N3 95 · N2 90 · N1 100). */
    private int passTotal;

    private boolean passed;

    /** Vì sao đỗ hoặc vì sao trượt, bằng tiếng Việt, đã sẵn sàng để hiện thẳng. */
    private String verdict;

    /** Cảnh báo rằng đây là điểm ƯỚC LƯỢNG. */
    private String disclaimer;

    /** Một ô trên bảng điểm. */
    @Data
    @Builder
    public static class Group {
        /** Tên enum, để client tự chọn cách hiển thị nếu cần. */
        private String code;
        /** "言語知識（文字・語彙・文法）" */
        private String japaneseName;
        /** "Kiến thức ngôn ngữ" */
        private String vietnameseName;

        private int score;
        private int maxScore;
        /** Điểm liệt của ô này (19 với ô 60 điểm, 38 với ô 120 điểm). */
        private int minScore;

        /** Ô này có qua điểm liệt không. */
        private boolean aboveMinimum;

        /** Số câu của ô này trong đề, và số câu làm đúng — để đối chiếu. */
        private int totalQuestions;
        private int correctQuestions;
    }
}
