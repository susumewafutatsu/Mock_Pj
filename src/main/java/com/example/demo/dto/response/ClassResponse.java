package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ClassResponse {

    private Integer classId;
    private String className;
    private String courseCode;

    /** Email giáo viên phụ trách */
    private String teacherEmail;
    private String teacherName;

    /** Thông tin trình độ */
    private Integer levelId;
    private String levelName;
    private Integer subjectId;
    private String subjectName;

    /** Sĩ số (đếm từ ClassStudents) */
    private long studentCount;

    private String googleClassroomId;
    private LocalDateTime createdAt;
}
