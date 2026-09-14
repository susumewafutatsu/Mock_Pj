package com.example.demo.controller;

import com.example.demo.dto.request.RoomCreateRequest;
import com.example.demo.dto.request.RoomDuplicateRequest;
import com.example.demo.dto.request.RoomJoinRequest;
import com.example.demo.dto.request.RoomUpdateRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.LeaderboardResponse;
import com.example.demo.dto.response.RoomMemberResponse;
import com.example.demo.dto.response.RoomMonitorResponse;
import com.example.demo.dto.response.RoomResponse;
import com.example.demo.service.LeaderboardService;
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

/** Phòng thi — thay cho toàn bộ nhóm endpoint quản lý lớp học cũ. */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final LeaderboardService leaderboardService;

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

    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> detail(@PathVariable Integer roomId,
                                            @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(roomService.getRoom(me.getUsername(), roomId));
    }

    /** Tiến độ làm bài của cả phòng. */
    @GetMapping("/{roomId}/monitor")
    public ApiResponse<RoomMonitorResponse> monitor(@PathVariable Integer roomId,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(roomService.monitor(me.getUsername(), roomId));
    }

    /** Nhân bản phòng cho buổi thi sau. */
    @PostMapping("/{roomId}/duplicate")
    public ApiResponse<RoomResponse> duplicate(@PathVariable Integer roomId,
                                               @Valid @RequestBody(required = false) RoomDuplicateRequest request,
                                               @AuthenticationPrincipal UserDetails me) {
        RoomResponse room = roomService.duplicateRoom(me.getUsername(), roomId, request);
        return ApiResponse.success("Đã tạo phòng mới. Mã tham gia: " + room.getCode(), room);
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

    /** "Bắt đầu làm bài": sảnh chờ → đang thi, ngay lúc này. */
    @PostMapping("/{roomId}/start")
    public ApiResponse<RoomResponse> start(@PathVariable Integer roomId,
                                           @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã bắt đầu làm bài",
                roomService.startExam(me.getUsername(), roomId));
    }

    /** Kết thúc phòng. Đang thi thì thu bài cả phòng ngay và mở bảng xếp hạng. */
    @PostMapping("/{roomId}/end")
    public ApiResponse<RoomResponse> end(@PathVariable Integer roomId,
                                         @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success("Đã kết thúc phòng thi",
                roomService.endExam(me.getUsername(), roomId));
    }

    /** Bảng xếp hạng + kết quả từng thí sinh, mỗi đề một bảng. */
    @GetMapping("/{roomId}/leaderboard")
    public ApiResponse<LeaderboardResponse> leaderboard(@PathVariable Integer roomId,
                                                        @AuthenticationPrincipal UserDetails me) {
        return ApiResponse.success(leaderboardService.roomLeaderboard(me.getUsername(), roomId));
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

    /** Vào phòng bằng mã. */
    @PostMapping("/join")
    public ApiResponse<RoomResponse> join(@Valid @RequestBody RoomJoinRequest request,
                                          @AuthenticationPrincipal UserDetails me) {
        RoomResponse room = roomService.join(me.getUsername(), request);
        return ApiResponse.success(
                "Đã vào phòng " + room.getName() + " — ghế số " + room.getMySeatNo(), room);
    }

    /** Vào phòng công khai không cần mã. */
    @PostMapping("/{roomId}/join-open")
    public ApiResponse<RoomResponse> joinOpen(@PathVariable Integer roomId,
                                              @AuthenticationPrincipal UserDetails me) {
        RoomResponse room = roomService.joinOpenRoom(me.getUsername(), roomId);
        return ApiResponse.success(
                "Đã vào phòng " + room.getName() + " — ghế số " + room.getMySeatNo(), room);
    }

    /** Báo đang mở trang phòng. */
    @PostMapping("/{roomId}/presence")
    public ApiResponse<Void> presence(@PathVariable Integer roomId,
                                      @AuthenticationPrincipal UserDetails me) {
        roomService.presence(me.getUsername(), roomId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{roomId}/membership")
    public ApiResponse<Void> leave(@PathVariable Integer roomId,
                                   @AuthenticationPrincipal UserDetails me) {
        roomService.leave(me.getUsername(), roomId);
        return ApiResponse.success("Đã rời phòng", null);
    }
}
