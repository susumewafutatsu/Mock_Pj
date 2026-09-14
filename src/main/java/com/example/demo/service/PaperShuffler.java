package com.example.demo.service;

import com.example.demo.dto.response.ExamOptionView;
import com.example.demo.dto.response.ExamQuestionView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Xáo đề theo từng lượt làm. */
public final class PaperShuffler {

    private PaperShuffler() {
    }

    /**
     * @param questions đề đã theo thứ tự gốc; danh sách này không bị sửa
     * @return danh sách mới theo thứ tự đã xáo (hoặc nguyên thứ tự nếu cả hai cờ tắt)
     */
    public static List<ExamQuestionView> shuffle(List<ExamQuestionView> questions,
                                                 boolean shuffleQuestions,
                                                 boolean shuffleOptions,
                                                 long seed) {
        List<ExamQuestionView> result = new ArrayList<>(questions);

        if (shuffleOptions) {
            for (ExamQuestionView question : result) {
                List<ExamOptionView> options = question.getOptions();
                if (options == null || options.size() < 2) {
                    continue;
                }
                List<ExamOptionView> copy = new ArrayList<>(options);
                // Hạt giống riêng cho từng câu: dùng chung một Random cho mọi câu thì thứ tự đáp án của câu sau phụ thuộc số đáp án của câu trước, sửa một câu là cả đề đổi.
                java.util.Collections.shuffle(copy, new Random(seed * 1_000_003L
                        + Objects.hashCode(question.getQuestionId())));
                question.setOptions(copy);
            }
        }

        if (!shuffleQuestions || result.size() < 2) {
            return result;
        }

        // Gom thành khối theo (phần thi → khối).
        Map<Integer, List<List<ExamQuestionView>>> blocksBySection = new LinkedHashMap<>();
        List<ExamQuestionView> currentBlock = null;
        Integer currentPassage = null;
        Integer currentSection = null;

        for (ExamQuestionView question : result) {
            Integer section = question.getSectionId();
            Integer passage = question.getPassageId();
            boolean sameBlock = currentBlock != null
                    && passage != null
                    && passage.equals(currentPassage)
                    && Objects.equals(section, currentSection);
            if (!sameBlock) {
                currentBlock = new ArrayList<>();
                blocksBySection.computeIfAbsent(section, k -> new ArrayList<>()).add(currentBlock);
            }
            currentBlock.add(question);
            currentPassage = passage;
            currentSection = section;
        }

        Random random = new Random(seed);
        List<ExamQuestionView> ordered = new ArrayList<>(result.size());
        for (List<List<ExamQuestionView>> blocks : blocksBySection.values()) {
            java.util.Collections.shuffle(blocks, random);
            for (List<ExamQuestionView> block : blocks) {
                ordered.addAll(block);
            }
        }
        return ordered;
    }
}
