package com.example.demo.service;

import com.example.demo.dto.response.LeaderboardResponse;

/** Bảng xếp hạng. */
public interface LeaderboardService {

    LeaderboardResponse roomLeaderboard(String viewerEmail, Integer roomId);

    LeaderboardResponse examLeaderboard(String viewerEmail, Integer examId);
}
