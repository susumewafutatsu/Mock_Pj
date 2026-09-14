package com.example.demo.service.impl;

import com.example.demo.domain.enums.Role;
import com.example.demo.domain.model.User;
import com.example.demo.dto.response.StudyStatsResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.repository.MistakeEntryRepository;
import com.example.demo.repository.UserCardStateRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.SrsService;
import com.example.demo.service.StudyService;
import com.example.demo.util.DbTime;
import com.example.demo.service.srs.Sm2Scheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Cài đặt bảng tổng quan việc học. */
@Service
@RequiredArgsConstructor
public class StudyServiceImpl implements StudyService {

    private final MistakeEntryRepository mistakeRepository;
    private final UserCardStateRepository cardStateRepository;
    private final UserRepository userRepository;
    private final SrsService srsService;

    @Override
    @Transactional(readOnly = true)
    public StudyStatsResponse getStats(String studentEmail) {
        User student = requireStudent(studentEmail);
        String userId = student.getUserId();
        LocalDateTime now = DbTime.now();

        return StudyStatsResponse.builder()
                .mistakesOpen(mistakeRepository.countOpen(userId))
                .mistakesDue(mistakeRepository.countDue(userId, now))
                .mistakesMastered(mistakeRepository
                        .countByUser_UserIdAndMasteredAtIsNotNull(userId))
                .cardsTotal(cardStateRepository.countByUser_UserId(userId))
                .cardsDue(srsService.cardsAvailableToday(userId))
                .cardsMature(cardStateRepository.countMature(
                        userId, Sm2Scheduler.MATURE_INTERVAL_DAYS))
                .build();
    }

    private User requireStudent(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        if (user.getRole() != Role.STUDENT) {
            throw new UnauthorizedException("Chỉ thí sinh mới có bảng học tập");
        }
        return user;
    }
}
