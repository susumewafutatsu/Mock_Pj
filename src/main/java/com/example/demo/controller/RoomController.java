package com.example.demo.controller;

import com.example.demo.dto.request.RoomCreateRequest;
import com.example.demo.dto.request.RoomJoinRequest;
import com.example.demo.dto.request.RoomUpdateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.RoomMemberResponse;
import com.example.demo.dto.response.RoomResponse;
import com.example.demo.service.RoomService;
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

/**
 * Phòng thi — thay cho toàn bộ nhóm endpoint quản lý lớp học cũ.
 *
 * Đặt ở {@code /api/rooms} chứ không dưới {@code /api/teacher} hay
 * {@code /api/student} là có chủ đích: phòng thi là nơi HAI vai gặp nhau. Người
 * ra đề mở phòng và gắn đề, thí sinh vào phòng và làm bài — cùng một tài
 * nguyên, hai góc nhìn. Tách đôi theo vai sẽ sinh ra hai bộ endpoint gần
 * giống hệt nhau cho cùng một thứ.
 *
 * Vì nhánh này chỉ yêu cầu "đã đăng nhập", việc phân quyền nằm ở tầng service:
 * mở phòng đòi vai người ra đề, còn mọi thao tác trên một phòng cụ thể đều
 * kiểm chủ sở hữu. Người không phải chủ phòng nhận 404 chứ không phải 403 —
 * không tiết lộ phòng đó có tồn tại hay không.
 *
 * Người ra đề:
 *   GET    /rooms/mine                  -> phòng tôi mở
 *   POST   /rooms                       -> mở phòng mới
 *   PUT    /rooms/{id}                  -> sửa phòng (mã phòng không sửa được)
 *   DELETE /rooms/{id}                  -> xoá phòng (chỉ khi chưa ai vào)
 *   GET    /rooms/{id}/members          -> ai đang trong phòng
 *   DELETE /rooms/{id}/members/{userId} -> mời một người ra
 *   POST   /rooms/{id}/exams/{examId}   -> gắn đề vào phòng
 *   DELETE /rooms/{id}/exams/{examId}   -> gỡ đề khỏi phòng
 *
 * Thí sinh:
 *   GET    /rooms/joined                -> phòng tôi đang tham gia
 *   GET    /rooms/open                  -> phòng mở, ai cũng vào được
 *   POST   /rooms/join                  -> vào phòng bằng mã
 *   DELETE /rooms/{id}/membership       -> tự rời phòng
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // ── Người ra đề ─────────────────────────────────────────────────────────

    @GetMapping("/mine")
    public ApiResponse<List<RoomResponse>> myRooms(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(roomService.getMyRooms(me.getUsername()));
    }

    /** Mở phòng mới. Phòng bắt đầu ở trạng thái nháp cho tới khi gắn đề và mở. */
    @PostMapping
    public ApiResponse<RoomResponse> create(@Valid @RequestBody RoomCreateRequest request,
                                            @AuthenticationPrincipal UserDetails me) {
        RoomResponse room = roomService.createRoom(me.getUsername(), request);
        return ApiResponse.success(
                "Đã mở phòng. Mã tham gia: " + room.getCode(), room);
    }

    @PutMapping("/{roomId}")
    public ApiResponse<RoomResponse> update(@PathVariable Integer roomId,
                                            @Valid @RequestBody RoomUpdateRequest request,
                                            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã cập nhật phòng",
                roomService.updateRoom(me.getUsername(), roomId, request));
    }

    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> delete(@PathVariable Integer roomId,
                                    @AuthenticationPrincipal UserDetails me) {
        roomService.deleteRoom(me.getUsername(), roomId);
        return ApiResponse.success("Đã xoá phòng", null);
    }

    @GetMapping("/{roomId}/members")
    public ApiResponse<List<RoomMemberResponse>> members(@PathVariable Integer roomId,
                                                         @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(roomService.getMembers(me.getUsername(), roomId));
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    public ApiResponse<Void> kick(@PathVariable Integer roomId,
                                  @PathVariable String userId,
                                  @AuthenticationPrincipal UserDetails me) {
        roomService.kickMember(me.getUsername(), roomId, userId);
        return ApiResponse.success("Đã mời ra khỏi phòng", null);
    }

    @PostMapping("/{roomId}/exams/{examId}")
    public ApiResponse<RoomResponse> attachExam(@PathVariable Integer roomId,
                                                @PathVariable Integer examId,
                                                @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã gắn bài thi vào phòng",
                roomService.attachExam(me.getUsername(), roomId, examId));
    }

    /** Gỡ đề khỏi phòng. Đề vẫn còn trong ngân hàng, bài đã làm vẫn còn nguyên. */
    @DeleteMapping("/{roomId}/exams/{examId}")
    public ApiResponse<RoomResponse> detachExam(@PathVariable Integer roomId,
                                                @PathVariable Integer examId,
                                                @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã gỡ bài thi khỏi phòng",
                roomService.detachExam(me.getUsername(), roomId, examId));
    }

    // ── Thí sinh ────────────────────────────────────────────────────────────

    @GetMapping("/joined")
    public ApiResponse<List<RoomResponse>> joined(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(roomService.getJoinedRooms(me.getUsername()));
    }

    /** Phòng mở cho bất kỳ ai — không cần mã. Danh sách này không kèm mã phòng. */
    @GetMapping("/open")
    public ApiResponse<List<RoomResponse>> open(@AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(roomService.browseOpenRooms(me.getUsername()));
    }

    /**
     * Vào phòng bằng mã.
     *
     * Idempotent: đã ở trong phòng thì nhận lại đúng ghế cũ. Phòng hết chỗ trả
     * 409 — và đó là câu trả lời đúng cho "ai nhanh thì vào".
     */
    @PostMapping("/join")
    public ApiResponse<RoomResponse> join(@Valid @RequestBody RoomJoinRequest request,
                                          @AuthenticationPrincipal UserDetails me) {
        RoomResponse room = roomService.join(me.getUsername(), request);
        return ApiResponse.success(
                "Đã vào phòng " + room.getName() + " — ghế số " + room.getMySeatNo(), room);
    }

    @DeleteMapping("/{roomId}/membership")
    public ApiResponse<Void> leave(@PathVariable Integer roomId,
                                   @AuthenticationPrincipal UserDetails me) {
        roomService.leave(me.getUsername(), roomId);
        return ApiResponse.success("Đã rời phòng", null);
    }
}
