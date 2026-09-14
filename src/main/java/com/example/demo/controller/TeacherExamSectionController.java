package com.example.demo.controller;

import com.example.demo.dto.request.ExamSectionRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.ExamSectionView;
import com.example.demo.service.TeacherExamSectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phần thi của một đề (JLPT thi theo phần, mỗi phần một đồng hồ riêng). */
@RestController
@RequestMapping("/api/teacher/exams/{examId}/sections")
@RequiredArgsConstructor
public class TeacherExamSectionController {

    private final TeacherExamSectionService sectionService;

    @GetMapping
    public ApiResponse<List<ExamSectionView>> list(
            @PathVariable Integer examId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(sectionService.list(examId, me.getUsername()));
    }

    /** Thay toàn bộ danh sách phần. Gửi mảng rỗng = bỏ chia phần. */
    @PutMapping
    public ApiResponse<List<ExamSectionView>> replaceAll(
            @PathVariable Integer examId,
            @Valid @RequestBody List<ExamSectionRequest> sections,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã lưu cấu trúc phần thi",
                sectionService.replaceAll(examId, sections, me.getUsername()));
    }

    /** Áp cấu trúc chuẩn của cấp JLPT gắn với đề, và bật chấm theo thang quy đổi. */
    @PostMapping("/jlpt-template")
    public ApiResponse<List<ExamSectionView>> applyTemplate(
            @PathVariable Integer examId,
            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã áp cấu trúc chuẩn JLPT và bật chấm theo thang quy đổi",
                sectionService.applyJlptTemplate(examId, me.getUsername()));
    }

    @DeleteMapping
    public ApiResponse<Void> deleteAll(
            @PathVariable Integer examId,
            @AuthenticationPrincipal UserDetails me) {
        sectionService.deleteAll(examId, me.getUsername());
        return ApiResponse.success("Đã bỏ chia phần cho đề này", null);
    }
}
