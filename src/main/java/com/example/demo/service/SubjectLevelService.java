package com.example.demo.service;

import com.example.demo.dto.response.SubjectLevelResponse;

import java.util.List;

/**
 * Danh mục môn học / trình độ. Chỉ đọc — dữ liệu do Admin quản lý.
 */
public interface SubjectLevelService {

    /** Toàn bộ trình độ, sắp xếp theo môn học rồi tới DisplayOrder */
    List<SubjectLevelResponse> listAll();
}
