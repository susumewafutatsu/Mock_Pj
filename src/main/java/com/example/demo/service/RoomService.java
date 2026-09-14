package com.example.demo.service;

import com.example.demo.dto.request.RoomCreateRequest;
import com.example.demo.dto.request.RoomDuplicateRequest;
import com.example.demo.dto.request.RoomJoinRequest;
import com.example.demo.dto.request.RoomUpdateRequest;
import com.example.demo.dto.response.RoomMemberResponse;
import com.example.demo.dto.response.RoomMonitorResponse;
import com.example.demo.dto.response.RoomResponse;

import java.util.List;

/** Phòng thi: một buổi thi cho một đề. */
public interface RoomService {

    // ── Người ra đề ─────────────────────────────────────────────────────────

    List<RoomResponse> getMyRooms(String ownerEmail);

    /** Chi tiết một phòng cho chủ phòng hoặc thành viên. */
    RoomResponse getRoom(String viewerEmail, Integer roomId);

    RoomResponse createRoom(String ownerEmail, RoomCreateRequest request);

    RoomResponse updateRoom(String ownerEmail, Integer roomId, RoomUpdateRequest request);

    /** Xoá phòng khi chưa ai từng vào. */
    void deleteRoom(String ownerEmail, Integer roomId);

    List<RoomMemberResponse> getMembers(String ownerEmail, Integer roomId);

    /** Theo dõi trực tiếp tiến độ làm bài của cả phòng. */
    RoomMonitorResponse monitor(String ownerEmail, Integer roomId);

    /** Mời ra khỏi phòng; đang thi thì thu bài ngay. */
    void kickMember(String ownerEmail, Integer roomId, String userId);

    RoomResponse attachExam(String ownerEmail, Integer roomId, Integer examId);

    RoomResponse detachExam(String ownerEmail, Integer roomId, Integer examId);

    RoomResponse startExam(String ownerEmail, Integer roomId);

    RoomResponse endExam(String ownerEmail, Integer roomId);

    /** Tạo buổi thi mới từ phòng cũ. */
    RoomResponse duplicateRoom(String ownerEmail, Integer roomId, RoomDuplicateRequest request);

    // ── Thí sinh ────────────────────────────────────────────────────────────

    List<RoomResponse> getJoinedRooms(String userEmail);

    /** Phòng công khai đang nhận người. */
    List<RoomResponse> browseOpenRooms(String userEmail);

    RoomResponse join(String userEmail, RoomJoinRequest request);

    /** Vào phòng công khai không cần mã. */
    RoomResponse joinOpenRoom(String userEmail, Integer roomId);

    void leave(String userEmail, Integer roomId);

    /** Đánh dấu thí sinh đang mở trang phòng. */
    void presence(String userEmail, Integer roomId);

    // ── Job nền ─────────────────────────────────────────────────────────────

    /** Gửi thông báo nhắc / bắt đầu / kết thúc cho phòng hẹn giờ. @return số thông báo gửi */
    int notifyScheduledRooms();
}
