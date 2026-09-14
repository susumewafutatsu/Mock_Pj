package com.example.demo.dto.response;

import com.example.demo.domain.enums.JoinPolicy;
import com.example.demo.domain.enums.MemberStatus;
import com.example.demo.domain.enums.RoomPhase;
import com.example.demo.domain.enums.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một phòng thi, kèm bối cảnh của người đang xem. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {

    private Integer roomId;
    private String name;

    /** Mã tham gia. Chỉ trả cho chủ phòng và thành viên. */
    private String code;

    private String ownerName;
    private Integer levelId;
    private String levelName;
    private String subjectName;

    /** null = không giới hạn người. */
    private Integer capacity;
    private long memberCount;
    private Integer seatsLeft;

    /** Số thí sinh đang mở trang phòng (chỉ trả cho chủ phòng). */
    private Long onlineCount;

    private JoinPolicy joinPolicy;
    private RoomStatus status;
    private RoomPhase phase;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private LocalDateTime serverTime;

    /** Đề của buổi thi. */
    private Integer examId;
    private String examTitle;
    private Integer examQuestionCount;
    private long examCount;

    private String instructions;
    private Integer lateJoinMinutes;

    /** Mốc cuối còn cho vào phòng khi đang thi. */
    private LocalDateTime lateJoinUntil;

    /** Phòng đang nhận người vào. */
    private boolean acceptingMembers;

    /** Xoá được (chưa ai từng vào). */
    private boolean deletable;

    /** Người đang xem là chủ phòng. */
    private boolean owner;

    private MemberStatus myStatus;
    private Integer mySeatNo;
}
