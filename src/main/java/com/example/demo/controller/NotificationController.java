package com.example.demo.controller;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Ô chuông — dùng chung cho mọi vai trò, mỗi người chỉ thấy thông báo của mình. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(notificationService.listMine(me.getUsername()));
    }

    /** Tách riêng để thanh trên hỏi nhẹ mỗi lần chuyển trang mà không kéo cả danh sách. */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(Map.of("unread", notificationService.unreadCount(me.getUsername())));
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable Integer notificationId,
                                      @AuthenticationPrincipal UserDetails me) {
        notificationService.markRead(me.getUsername(), notificationId);
        return ApiResponse.success(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(Map.of("updated", notificationService.markAllRead(me.getUsername())));
    }
}
