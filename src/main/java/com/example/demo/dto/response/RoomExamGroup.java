package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Đề thi của một phòng, đã nhóm sẵn cho trang chủ thí sinh. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomExamGroup {

    private Integer roomId;

    private String roomName;

    private String subjectName;

    private String levelName;

    private String ownerName;

    /** Số đề trong phòng này thí sinh còn phải làm. */
    private int pendingCount;

    /** Sảnh chờ / đang thi / đã kết thúc — xem RoomPhase. */
    private com.example.demo.domain.enums.RoomPhase phase;

    private java.time.LocalDateTime startTime;

    private java.time.LocalDateTime endTime;

    private List<ExamResponse> exams;
}
