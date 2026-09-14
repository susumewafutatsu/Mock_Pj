package com.example.demo.service;

import com.example.demo.dto.response.StudyStatsResponse;

/** Bảng tổng quan việc học của một thí sinh. */
public interface StudyService {

    /** Số liệu cho bảng "học hôm nay". */
    StudyStatsResponse getStats(String studentEmail);
}
