package com.example.demo.dto.response;

import com.example.demo.domain.enums.MemberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một người trong phòng thi. Thay cho ClassStudentResponse cũ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberResponse {

    private String userId;

    private String fullName;

    private String email;

    /** Số ghế theo thứ tự vào phòng. */
    private Integer seatNo;

    private MemberStatus status;

    private LocalDateTime joinedAt;
}
