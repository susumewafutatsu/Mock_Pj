package com.example.demo.service;

import com.example.demo.dto.request.RoomCreateRequest;
import com.example.demo.dto.request.RoomJoinRequest;
import com.example.demo.dto.request.RoomUpdateRequest;
import com.example.demo.dto.response.RoomMemberResponse;
import com.example.demo.dto.response.RoomResponse;

import java.util.List;

/**
 * Phòng thi — thay cho toàn bộ nghiệp vụ Lớp học cũ.
 *
 * Khác biệt về mặt nghiệp vụ, không chỉ là đổi tên:
 *
 *   - Người ra đề không thêm từng người vào phòng nữa. Họ mở phòng, đặt sức
 *     chứa, đọc mã; thí sinh tự vào. Vì thế không có {@code addMember} — chỉ
 *     có {@link #join} do chính thí sinh gọi.
 *   - Đề thi gắn vào phòng qua một bảng nối, nên gỡ đề khỏi phòng không xoá
 *     đề, và cùng một đề gắn được vào nhiều phòng.
 *   - Phòng có sức chứa, nên việc vào phòng là một cuộc tranh chấp thật. Xem
 *     {@link #join} về cách chống tràn.
 */
public interface RoomService {

    // ── Phía người ra đề ───────────────────────────────────────────────────

    /** Phòng do người này mở. */
    List<RoomResponse> getMyRooms(String ownerEmail);

    /** Mở phòng mới. Mã phòng do server sinh ngẫu nhiên. */
    RoomResponse createRoom(String ownerEmail, RoomCreateRequest request);

    /** Sửa phòng. Chỉ chủ phòng. Mã phòng không sửa được. */
    RoomResponse updateRoom(String ownerEmail, Integer roomId, RoomUpdateRequest request);

    /**
     * Xoá phòng. Chỉ chủ phòng, và chỉ khi chưa có ai vào.
     *
     * Phòng đã có người thì đóng lại ({@code status = CLOSED}) chứ không xoá:
     * xoá sẽ kéo theo cả lịch sử ai từng ở trong phòng nào.
     */
    void deleteRoom(String ownerEmail, Integer roomId);

    /** Danh sách người trong phòng, theo thứ tự ghế. Chỉ chủ phòng xem được. */
    List<RoomMemberResponse> getMembers(String ownerEmail, Integer roomId);

    /** Mời một người ra khỏi phòng. Giữ ghế và giữ lịch sử, chỉ đổi trạng thái. */
    void kickMember(String ownerEmail, Integer roomId, String userId);

    /** Gắn một đề thi vào phòng. Đề phải do chính người này tạo. */
    RoomResponse attachExam(String ownerEmail, Integer roomId, Integer examId);

    /** Gỡ đề khỏi phòng. Không xoá đề. */
    RoomResponse detachExam(String ownerEmail, Integer roomId, Integer examId);

    // ── Phía thí sinh ──────────────────────────────────────────────────────

    /** Các phòng thí sinh đang tham gia. */
    List<RoomResponse> getJoinedRooms(String userEmail);

    /** Phòng đang mở cho bất kỳ ai vào — không cần mã. */
    List<RoomResponse> browseOpenRooms(String userEmail);

    /**
     * Vào phòng bằng mã.
     *
     * Đây là chỗ "ai nhanh thì vào" xảy ra thật: hai trăm người bấm cùng lúc
     * vào phòng năm mươi chỗ. Cách chống tràn nằm ở phần cài đặt; điều duy
     * nhất người gọi cần biết là phương thức này idempotent — người đã ở trong
     * phòng gọi lại sẽ nhận về đúng ghế cũ, không cấp ghế mới.
     */
    RoomResponse join(String userEmail, RoomJoinRequest request);

    /** Tự rời phòng. */
    void leave(String userEmail, Integer roomId);
}
