package com.example.demo.service;

import com.example.demo.domain.enums.JlptSkill;
import com.example.demo.domain.enums.SubmissionStatus;
import com.example.demo.domain.model.Exam;
import com.example.demo.domain.model.ExamSubmission;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.StudyInsightResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ExamQuestionRepository;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.ExamSubmissionRepository;
import com.example.demo.repository.SubmissionDetailRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Gợi ý ôn theo điểm yếu + bài xếp trình độ đầu vào. */
@Service
@RequiredArgsConstructor
public class StudyInsightService {

    /** Chủ điểm phải có ít nhất chừng này lượt làm mới được gọi là "yếu" — một câu sai chưa nói lên gì. */
    static final int MIN_ANSWERS = 3;

    /** Ngưỡng coi là đã vững một cấp trong bài xếp trình độ. */
    static final int LEVEL_PASS_PERCENT = 60;

    private static final int WEAK_TAG_LIMIT = 3;
    private static final int EXAMS_PER_TAG = 3;

    private final UserRepository userRepository;
    private final SubmissionDetailRepository detailRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamRepository examRepository;
    private final ExamSubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public StudyInsightResponse insights(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
        String userId = user.getUserId();

        List<StudyInsightResponse.SkillStat> skills = new ArrayList<>();
        for (Object[] row : detailRepository.skillStatsOfStudent(userId)) {
            JlptSkill skill = (JlptSkill) row[0];
            long answered = ((Number) row[1]).longValue();
            long correct = row[2] == null ? 0 : ((Number) row[2]).longValue();
            skills.add(StudyInsightResponse.SkillStat.builder()
                    .skill(skill.name())
                    .japaneseName(skill.getJapaneseName())
                    .vietnameseName(skill.getVietnameseName())
                    .answered(answered)
                    .correct(correct)
                    .percent(percent(correct, answered))
                    .build());
        }
        skills.sort(Comparator.comparing(StudyInsightResponse.SkillStat::getPercent,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<StudyInsightResponse.WeakTag> weak = new ArrayList<>();
        for (Object[] row : detailRepository.tagStatsOfStudent(userId)) {
            long answered = ((Number) row[2]).longValue();
            long correct = row[3] == null ? 0 : ((Number) row[3]).longValue();
            if (answered < MIN_ANSWERS) {
                continue;
            }
            weak.add(StudyInsightResponse.WeakTag.builder()
                    .tagId((Integer) row[0])
                    .tagName((String) row[1])
                    .answered(answered)
                    .correct(correct)
                    .percent(percent(correct, answered))
                    .build());
        }
        weak.sort(Comparator.comparing(StudyInsightResponse.WeakTag::getPercent));
        // Chỉ gợi ý chủ điểm thật sự yếu. Đúng 90% mà vẫn bị gọi là "điểm yếu"
        // thì học viên sẽ thôi đọc khối này.
        weak = weak.stream().filter(t -> t.getPercent() < 80).limit(WEAK_TAG_LIMIT).toList();
        for (StudyInsightResponse.WeakTag tag : weak) {
            tag.setExams(examQuestionRepository.findPublicExamsByTag(tag.getTagId()).stream()
                    .limit(EXAMS_PER_TAG)
                    .map(this::suggested)
                    .toList());
        }

        return StudyInsightResponse.builder()
                .skills(skills)
                .weakTags(weak)
                .placement(placement(userId))
                .build();
    }

    private StudyInsightResponse.Placement placement(String userId) {
        Optional<Exam> found = examRepository.findFirstByIsPlacementTrueAndIsPublicTrueOrderByExamIdDesc();
        if (found.isEmpty()) {
            return null;
        }
        Exam exam = found.get();
        Optional<ExamSubmission> latest = submissionRepository
                .findFirstByExam_ExamIdAndStudent_UserIdOrderByAttemptNumberDesc(exam.getExamId(), userId)
                .filter(s -> s.getStatus() != SubmissionStatus.IN_PROGRESS);

        StudyInsightResponse.Placement.PlacementBuilder view = StudyInsightResponse.Placement.builder()
                .examId(exam.getExamId())
                .title(exam.getTitle());

        if (latest.isEmpty()) {
            return view.taken(false)
                    .advice("Làm bài xếp trình độ để biết nên bắt đầu ôn từ cấp nào — "
                            + "đỡ phí công ôn lại thứ đã vững, hoặc nhảy cóc qua thứ còn hổng.")
                    .build();
        }

        List<StudyInsightResponse.LevelStat> levels = new ArrayList<>();
        for (Object[] row : detailRepository.levelStatsOfSubmission(latest.get().getSubmissionId())) {
            long answered = ((Number) row[2]).longValue();
            long correct = row[3] == null ? 0 : ((Number) row[3]).longValue();
            levels.add(StudyInsightResponse.LevelStat.builder()
                    .levelName((String) row[0])
                    .answered(answered)
                    .correct(correct)
                    .percent(percent(correct, answered))
                    .build());
        }

        String recommended = recommendLevel(levels);
        return view.taken(true)
                .submissionId(latest.get().getSubmissionId())
                .levels(levels)
                .recommendedLevel(recommended)
                .advice(adviceFor(recommended, levels))
                .build();
    }

    /** Cấp nên bắt đầu = cấp DỄ NHẤT chưa vững. */
    static String recommendLevel(List<StudyInsightResponse.LevelStat> levels) {
        if (levels == null || levels.isEmpty()) {
            return null;
        }
        for (StudyInsightResponse.LevelStat level : levels) {
            if (level.getPercent() == null || level.getPercent() < LEVEL_PASS_PERCENT) {
                return level.getLevelName();
            }
        }
        // Vững hết mọi cấp có trong bài: ôn tiếp ở cấp khó nhất đã kiểm tra.
        return levels.get(levels.size() - 1).getLevelName();
    }

    private String adviceFor(String recommended, List<StudyInsightResponse.LevelStat> levels) {
        if (recommended == null) {
            return "Bài xếp trình độ chưa có câu nào gắn cấp, chưa đưa ra gợi ý được.";
        }
        boolean allPassed = levels.stream()
                .allMatch(l -> l.getPercent() != null && l.getPercent() >= LEVEL_PASS_PERCENT);
        if (allPassed) {
            return "Bạn vững mọi cấp trong bài xếp trình độ. Hãy ôn và thi thử ở " + recommended
                    + " — hoặc thử sức cấp cao hơn.";
        }
        return "Nên bắt đầu ôn từ " + recommended + ": đây là cấp dễ nhất bạn đúng dưới "
                + LEVEL_PASS_PERCENT + "%. Vá cấp này trước thì các cấp sau mới đứng vững.";
    }

    private StudyInsightResponse.SuggestedExam suggested(Exam exam) {
        return StudyInsightResponse.SuggestedExam.builder()
                .examId(exam.getExamId())
                .title(exam.getTitle())
                .levelName(exam.getLevel() == null ? null : exam.getLevel().getLevelName())
                .build();
    }

    private static Integer percent(long part, long whole) {
        return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
    }
}
