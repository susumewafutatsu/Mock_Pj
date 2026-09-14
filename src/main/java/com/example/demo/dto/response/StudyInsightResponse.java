package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** "Tôi nên ôn gì tiếp theo?" — trả lời bằng chính dữ liệu bài làm của học viên. */
@Data
@Builder
public class StudyInsightResponse {

    /** Tỉ lệ đúng theo kỹ năng JLPT, yếu nhất trước. */
    private List<SkillStat> skills;

    /** Tối đa 3 chủ điểm (tag) yếu nhất, mỗi cái kèm vài đề nhắm đúng chủ điểm đó. */
    private List<WeakTag> weakTags;

    /** Bài xếp trình độ; null khi hệ thống chưa có bài nào. */
    private Placement placement;

    @Data
    @Builder
    public static class SkillStat {
        private String skill;
        private String japaneseName;
        private String vietnameseName;
        private long answered;
        private long correct;
        private Integer percent;
    }

    @Data
    @Builder
    public static class WeakTag {
        private Integer tagId;
        private String tagName;
        private long answered;
        private long correct;
        private Integer percent;
        private List<SuggestedExam> exams;
    }

    @Data
    @Builder
    public static class SuggestedExam {
        private Integer examId;
        private String title;
        private String levelName;
    }

    @Data
    @Builder
    public static class Placement {
        private Integer examId;
        private String title;

        /** Đã nộp bài xếp trình độ chưa. */
        private boolean taken;
        private Integer submissionId;

        /** Cấp nên bắt đầu ôn, vd "N4". null khi chưa làm bài. */
        private String recommendedLevel;

        /** Tỉ lệ đúng theo từng cấp trong bài xếp trình độ, dễ → khó. */
        private List<LevelStat> levels;

        /** Câu giải thích bằng tiếng Việt, hiện thẳng cho học viên. */
        private String advice;
    }

    @Data
    @Builder
    public static class LevelStat {
        private String levelName;
        private long answered;
        private long correct;
        private Integer percent;
    }
}
