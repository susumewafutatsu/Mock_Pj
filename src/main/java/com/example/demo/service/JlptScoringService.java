package com.example.demo.service;

import com.example.demo.domain.enums.JlptLevel;
import com.example.demo.domain.enums.JlptScoreGroup;
import com.example.demo.domain.enums.JlptSkill;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamQuestion;
import com.example.demo.domain.model.SubmissionDetail;
import com.example.demo.dto.response.JlptScoreView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Quy điểm thô của một bài làm ra bảng điểm JLPT, rồi kết luận Đỗ / Trượt. */
@Service
public class JlptScoringService {

    public static final String DISCLAIMER =
            "Điểm quy đổi ở đây là ước lượng theo tỉ lệ câu đúng. Kỳ thi thật quy đổi "
            + "theo độ khó của từng câu nên con số có thể lệch đôi chút; cấu trúc phần thi, "
            + "ngưỡng đỗ và điểm liệt thì đúng như quy định.";

    /** Đề này có chấm theo JLPT được không. */
    public boolean applies(Exam exam, List<ExamQuestion> questions) {
        return levelOf(exam) != null
                && !questions.isEmpty()
                && questions.stream().allMatch(q -> q.resolveSkill() != null);
    }

    /** Cấp JLPT của đề, hoặc null khi đề không bật chấm JLPT / trình độ không phải N1–N5. */
    public JlptLevel levelOf(Exam exam) {
        if (!Boolean.TRUE.equals(exam.getJlptScoring()) || exam.getLevel() == null) {
            return null;
        }
        return JlptLevel.fromLevelName(exam.getLevel().getLevelName());
    }

    /** Dựng bảng điểm. */
    public JlptScoreView score(Exam exam, List<ExamQuestion> questions,
                              Map<Integer, SubmissionDetail> details) {
        JlptLevel level = levelOf(exam);
        if (level == null || !applies(exam, questions)) {
            return null;
        }

        // Cộng điểm thô theo từng ô của bảng điểm.
        Map<JlptScoreGroup, BigDecimal> raw = new LinkedHashMap<>();
        Map<JlptScoreGroup, BigDecimal> rawMax = new LinkedHashMap<>();
        Map<JlptScoreGroup, int[]> counts = new LinkedHashMap<>();
        for (JlptScoreGroup group : level.groups()) {
            raw.put(group, BigDecimal.ZERO);
            rawMax.put(group, BigDecimal.ZERO);
            counts.put(group, new int[]{0, 0});
        }

        for (ExamQuestion question : questions) {
            JlptSkill skill = question.resolveSkill();
            JlptScoreGroup group = level.groupOf(skill);
            BigDecimal points = question.getPoints() == null ? BigDecimal.ONE : question.getPoints();

            rawMax.merge(group, points, BigDecimal::add);
            counts.get(group)[0]++;

            SubmissionDetail detail = details.get(question.getId().getQuestionId());
            if (detail != null && Boolean.TRUE.equals(detail.getIsCorrect())) {
                raw.merge(group, detail.getScoreEarned() == null ? points : detail.getScoreEarned(),
                        BigDecimal::add);
                counts.get(group)[1]++;
            }
        }

        List<JlptScoreView.Group> views = new ArrayList<>();
        List<JlptScoreGroup> belowMinimum = new ArrayList<>();
        int total = 0;

        for (JlptScoreGroup group : level.groups()) {
            int max = level.maxScoreOf(group);
            int min = level.minScoreOf(group);
            int scaled = scale(raw.get(group), rawMax.get(group), max);
            boolean above = scaled >= min;
            if (!above) {
                belowMinimum.add(group);
            }
            total += scaled;

            int[] c = counts.get(group);
            views.add(JlptScoreView.Group.builder()
                    .code(group.name())
                    .japaneseName(group.getJapaneseName())
                    .vietnameseName(group.getVietnameseName())
                    .score(scaled)
                    .maxScore(max)
                    .minScore(min)
                    .aboveMinimum(above)
                    .totalQuestions(c[0])
                    .correctQuestions(c[1])
                    .build());
        }

        boolean totalOk = total >= level.getPassTotal();
        boolean passed = totalOk && belowMinimum.isEmpty();

        return JlptScoreView.builder()
                .level(level.name())
                .groups(views)
                .totalScore(total)
                .totalMax(JlptLevel.TOTAL_MAX)
                .passTotal(level.getPassTotal())
                .passed(passed)
                .verdict(verdict(level, total, totalOk, belowMinimum))
                .disclaimer(DISCLAIMER)
                .build();
    }

    /** Quy đổi tuyến tính. Nhóm không có câu nào (rawMax = 0) trả 0 chứ không chia cho 0. */
    private int scale(BigDecimal raw, BigDecimal rawMax, int maxScaled) {
        if (rawMax == null || rawMax.signum() == 0) {
            return 0;
        }
        return raw.multiply(BigDecimal.valueOf(maxScaled))
                .divide(rawMax, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** Câu kết luận. Viết ở server vì luật có hai vế và rất dễ diễn giải sai vế thứ hai. */
    private String verdict(JlptLevel level, int total, boolean totalOk,
                           List<JlptScoreGroup> belowMinimum) {
        if (totalOk && belowMinimum.isEmpty()) {
            return "ĐỖ — tổng " + total + "/" + JlptLevel.TOTAL_MAX
                    + " đạt ngưỡng " + level.getPassTotal() + " của " + level.name()
                    + ", và không phần nào dưới điểm liệt.";
        }

        StringBuilder reason = new StringBuilder("TRƯỢT — ");
        if (!totalOk) {
            reason.append("tổng ").append(total).append("/").append(JlptLevel.TOTAL_MAX)
                  .append(" chưa đạt ngưỡng ").append(level.getPassTotal())
                  .append(" của ").append(level.name());
        }
        if (!belowMinimum.isEmpty()) {
            if (!totalOk) {
                reason.append("; ngoài ra ");
            }
            // Đây là điều đáng nói nhất khi tổng đã đủ mà vẫn trượt: người học
            // cần biết chính xác nên đổ công vào phần nào.
            reason.append(belowMinimum.size() == 1 ? "phần " : "các phần ");
            for (int i = 0; i < belowMinimum.size(); i++) {
                JlptScoreGroup group = belowMinimum.get(i);
                if (i > 0) {
                    reason.append(", ");
                }
                reason.append(group.getVietnameseName())
                      .append(" (").append(group.getJapaneseName()).append(")");
            }
            reason.append(" dưới điểm liệt");
            if (totalOk) {
                reason.append(" — tổng đã đủ nhưng vẫn trượt vì học lệch");
            }
        }
        return reason.append('.').toString();
    }
}
