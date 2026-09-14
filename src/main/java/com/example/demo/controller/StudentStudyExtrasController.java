package com.example.demo.controller;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.BookmarkResponse;
import com.example.demo.dto.response.StudyInsightResponse;
import com.example.demo.service.BookmarkService;
import com.example.demo.service.StudyInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Hai thứ giúp học viên tự định hướng việc ôn */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentStudyExtrasController {

    private final BookmarkService bookmarkService;
    private final StudyInsightService insightService;

    @GetMapping("/bookmarks")
    public ApiResponse<List<BookmarkResponse>> bookmarks(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(bookmarkService.list(me.getUsername()));
    }

    /** Đánh dấu hoặc sửa ghi chú. Body: {"note": "..."} — có thể để trống. */
    @PutMapping("/bookmarks/{questionId}")
    public ApiResponse<BookmarkResponse> upsertBookmark(@PathVariable Integer questionId,
                                                        @RequestBody(required = false) Map<String, String> body,
                                                        @AuthenticationPrincipal UserDetails me) {
        String note = body == null ? null : body.get("note");
        return ApiResponse.success("Đã lưu câu đánh dấu",
                bookmarkService.upsert(me.getUsername(), questionId, note));
    }

    @DeleteMapping("/bookmarks/{questionId}")
    public ApiResponse<Void> removeBookmark(@PathVariable Integer questionId,
                                            @AuthenticationPrincipal UserDetails me) {
        bookmarkService.remove(me.getUsername(), questionId);
        return ApiResponse.success("Đã bỏ đánh dấu", null);
    }

    @GetMapping("/insights")
    public ApiResponse<StudyInsightResponse> insights(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(insightService.insights(me.getUsername()));
    }
}
