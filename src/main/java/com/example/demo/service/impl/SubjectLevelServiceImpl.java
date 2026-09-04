package com.example.demo.service.impl;

import com.example.demo.domain.model.Subject;
import com.example.demo.domain.model.SubjectLevel;
import com.example.demo.dto.response.SubjectLevelResponse;
import com.example.demo.repository.SubjectLevelRepository;
import com.example.demo.service.SubjectLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectLevelServiceImpl implements SubjectLevelService {

    private final SubjectLevelRepository subjectLevelRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubjectLevelResponse> listAll() {
        return subjectLevelRepository.findAll()
                .stream()
                .sorted(Comparator
                        .comparing((SubjectLevel lv) -> lv.getSubject() != null
                                ? lv.getSubject().getSubjectName() : "",
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(lv -> lv.getDisplayOrder() != null
                                ? lv.getDisplayOrder() : Integer.MAX_VALUE)
                        .thenComparing(SubjectLevel::getLevelId))
                .map(this::toResponse)
                .toList();
    }

    private SubjectLevelResponse toResponse(SubjectLevel lv) {
        Subject subject = lv.getSubject();
        return SubjectLevelResponse.builder()
                .levelId(lv.getLevelId())
                .levelName(lv.getLevelName())
                .subjectId(subject != null ? subject.getSubjectId() : null)
                .subjectName(subject != null ? subject.getSubjectName() : null)
                .displayOrder(lv.getDisplayOrder())
                .build();
    }
}
