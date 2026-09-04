package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Đề thi của một lớp, kèm đủ thông tin lớp để hiển thị tiêu đề nhóm.
 *
 * Nhóm theo lớp chứ không trả một danh sách phẳng, vì với học sinh học nhiều
 * lớp thì "đề này của môn nào" là thông tin phải thấy ngay, không phải đọc từng
 * dòng mới suy ra. Danh sách phẳng trước đây trộn đề của cả ba lớp lẫn đề luyện
 * tập vào cùng một chỗ.
 */
@Data
@Builder
public class ClassExamGroup {

    private Integer classId;
    private String className;
    private String courseCode;
    private String teacherName;
    private Integer levelId;
    private String levelName;
    private Integer subjectId;
    private String subjectName;

    /** Số đề học sinh còn phải làm trong lớp này — dùng cho huy hiệu trên tab lớp. */
    private int pendingCount;

    /** Đề của lớp, đã sắp theo mức độ cần xử lý. Có thể rỗng nếu lớp chưa có đề. */
    private List<ExamResponse> exams;
}
