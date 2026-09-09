package com.example.demo.dto.response;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.MemberStatus;
import com.example.demo.domain.enums.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một phòng thi, kèm bối cảnh của chính người đang xem.
 *
 * Thay cho ClassResponse cũ. Khác biệt đáng chú ý: có {@link #seatsLeft} và
 * {@link #myStatus} — hai thứ mô hình lớp không cần, vì lớp không có sức chứa
 * và ai không được thêm vào thì đơn giản là không thấy lớp học đó.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {

    private Integer roomId;

    private String name;

    /**
     * Mã tham gia. Chỉ trả cho chủ phòng và thành viên — người ngoài mà thấy
     * mã thì cơ chế "vào bằng mã" mất hết ý nghĩa.
     */
    private String code;

    private String ownerName;

    private Integer levelId;

    private String levelName;

    private String subjectName;

    /** null = không giới hạn người. */
    private Integer capacity;

    private long memberCount;

    /** Số ghế trống. null khi phòng không giới hạn. */
    private Integer seatsLeft;

    private JoinPolicy joinPolicy;

    private RoomStatus status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private long examCount;

    /** Người đang xem là chủ phòng. */
    private boolean owner;

    /** Tư cách của người đang xem. null = chưa từng vào phòng này. */
    private MemberStatus myStatus;

    /** Số ghế của người đang xem. null nếu chưa vào. */
    private Integer mySeatNo;
}
