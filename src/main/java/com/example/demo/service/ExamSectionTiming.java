package com.example.demo.service;

import com.example.demo.domain.model.ExamSection;
import com.example.demo.dto.response.ExamSectionView;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lịch chạy của các phần thi trong một phiên làm bài. */
public final class ExamSectionTiming {

    private final LocalDateTime startedAt;
    private final List<ExamSection> sections;

    /** sectionId → [mở lúc, khoá lúc] */
    private final Map<Integer, LocalDateTime[]> windows = new LinkedHashMap<>();

    public ExamSectionTiming(LocalDateTime startedAt, List<ExamSection> sections) {
        this.startedAt = startedAt;
        this.sections = sections == null ? List.of() : sections;

        LocalDateTime cursor = startedAt;
        for (ExamSection section : this.sections) {
            LocalDateTime end = cursor.plusMinutes(
                    section.getDurationMinutes() == null ? 0 : section.getDurationMinutes());
            windows.put(section.getSectionId(), new LocalDateTime[]{cursor, end});
            cursor = end;
        }
    }

    /** Đề này có chia phần không. Không chia = một đồng hồ duy nhất như trước. */
    public boolean hasSections() {
        return !sections.isEmpty();
    }

    /** Phần này còn nhận đáp án không. */
    public boolean isOpen(Integer sectionId, LocalDateTime now) {
        if (sectionId == null || !windows.containsKey(sectionId)) {
            // Câu không thuộc phần nào trong một đề có chia phần: coi như luôn mở.
            return true;
        }
        LocalDateTime[] window = windows.get(sectionId);
        return !notYetOpen(sectionId, now) && now.isBefore(window[1]);
    }

    /** Phần đang được làm ngay lúc này, hoặc null khi đã qua hết. */
    public ExamSection currentSection(LocalDateTime now) {
        for (ExamSection section : sections) {
            LocalDateTime[] window = windows.get(section.getSectionId());
            if (now.isBefore(window[1])) {
                return section;
            }
        }
        return null;
    }

    /** Câu giải thích vì sao phần này không nhận đáp án. */
    public String closedReason(Integer sectionId, LocalDateTime now) {
        String name = nameOf(sectionId);
        if (windows.containsKey(sectionId) && notYetOpen(sectionId, now)) {
            return "Phần \"" + name + "\" chưa tới lượt. Làm xong phần đang mở thì phần này tự bắt đầu.";
        }
        return "Phần \"" + name + "\" đã hết giờ, không sửa được đáp án nữa.";
    }

    /** Tên phần để đưa vào thông báo lỗi cho thí sinh. */
    public String nameOf(Integer sectionId) {
        return sections.stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .map(ExamSection::getName)
                .findFirst()
                .orElse("này");
    }

    /** Mốc phiên kết thúc nếu tính theo tổng các phần. */
    public LocalDateTime endOfLastSection() {
        if (sections.isEmpty()) {
            return startedAt;
        }
        ExamSection last = sections.get(sections.size() - 1);
        return windows.get(last.getSectionId())[1];
    }

    /** Dựng khung nhìn cho client. */
    public List<ExamSectionView> toViews(LocalDateTime now,
                                         Map<Integer, Integer> totalBySection,
                                         Map<Integer, Integer> answeredBySection) {
        ExamSection current = currentSection(now);
        List<ExamSectionView> views = new ArrayList<>();

        for (ExamSection section : sections) {
            LocalDateTime[] window = windows.get(section.getSectionId());
            boolean isCurrent = current != null && current.getSectionId().equals(section.getSectionId());
            boolean locked = !now.isBefore(window[1]);
            boolean upcoming = notYetOpen(section.getSectionId(), now);

            long remaining = isCurrent
                    ? Math.max(0, Duration.between(now, window[1]).getSeconds())
                    : 0;

            views.add(ExamSectionView.builder()
                    .sectionId(section.getSectionId())
                    .name(section.getName())
                    .orderNo(section.getOrderNo())
                    .durationMinutes(section.getDurationMinutes())
                    .startsAt(window[0])
                    .endsAt(window[1])
                    .current(isCurrent)
                    .locked(locked)
                    .upcoming(upcoming)
                    .remainingSeconds(remaining)
                    .totalQuestions(totalBySection.getOrDefault(section.getSectionId(), 0))
                    .answeredQuestions(answeredBySection.getOrDefault(section.getSectionId(), 0))
                    .build());
        }
        return views;
    }

    /** Chưa tới lượt phần này. Phần đầu không bao giờ "chưa tới lượt" — xem chú thích lớp. */
    private boolean notYetOpen(Integer sectionId, LocalDateTime now) {
        boolean first = !sections.isEmpty() && sections.get(0).getSectionId().equals(sectionId);
        return !first && now.isBefore(windows.get(sectionId)[0]);
    }
}
