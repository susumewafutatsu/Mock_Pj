package com.example.demo.service;

import com.example.demo.dto.request.SaveAnswerRequest;
import com.example.demo.dto.request.SubmitExamRequest;
import com.example.demo.dto.response.AnswerSavedResponse;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.ExamSessionResponse;
import com.example.demo.dto.response.HeartbeatResponse;

import java.util.List;

/**
 * Vòng đời một phiên làm bài của học sinh, từ lúc bắt đầu tới lúc có điểm.
 *
 * Ba nguyên tắc chi phối toàn bộ interface này:
 *
 * 1. Một học sinh — một đề — một phiên. {@link #startOrResume} không bao giờ
 *    tạo phiên thứ hai; gọi lại nó là "vào lại phòng thi", không phải "thi lại".
 *    Ràng buộc UNIQUE(ExamID, StudentID) ở DB là chốt cuối.
 *
 * 2. Thời gian do server quyết định. Deadline được chốt một lần vào ExpiresAt
 *    lúc bắt đầu thi; mọi endpoint đều so với cột đó và tự nộp bài nếu đã quá
 *    giờ. Client chỉ hiển thị đồng hồ, không có quyền phán xét còn giờ hay hết.
 *
 * 3. Tiến độ được ghi ngay, không đợi lúc nộp. {@link #saveAnswer} upsert từng
 *    câu vào SubmissionDetails, nên mất mạng giữa bài chỉ mất đúng câu đang
 *    chọn dở. {@link #heartbeat} chỉ để phát hiện rớt mạng, không bù giờ.
 */
public interface SubmissionService {

    /**
     * Vào phòng thi: tạo phiên nếu chưa có, trả lại phiên đang dở nếu đã có.
     *
     * @throws com.example.demo.exception.BusinessException nếu học sinh đã nộp
     *         bài đề này (không cho làm lại), đề chưa mở / đã đóng, hoặc phiên
     *         đang dở đã hết giờ (bài được nộp tự động trước khi báo lỗi).
     */
    ExamSessionResponse startOrResume(Integer examId, String studentEmail);

    /**
     * Đọc lại phiên đang dở mà không tạo mới — dùng cho luồng "khôi phục sau
     * khi mất kết nối". Trả về cả ExpiresAt đã lưu để client tính lại thời gian
     * còn lại theo giờ server.
     *
     * @throws com.example.demo.exception.ResourceNotFoundException nếu học sinh
     *         chưa từng bắt đầu đề này.
     */
    ExamSessionResponse getSession(Integer examId, String studentEmail);

    /**
     * Autosave một câu trả lời (upsert theo SubmissionID + QuestionID).
     * Gọi ngay mỗi lần học sinh chọn đáp án.
     */
    AnswerSavedResponse saveAnswer(Integer examId, SaveAnswerRequest request, String studentEmail);

    /**
     * Nhịp sống của client, 15-30 giây một lần. Cập nhật LastActiveAt và tắt
     * cờ AtRiskStatus. Nếu phát hiện đã quá giờ thì nộp bài tự động ngay tại đây.
     */
    HeartbeatResponse heartbeat(Integer examId, String studentEmail);

    /** Học sinh chủ động nộp bài. Chấm ngay các câu trắc nghiệm. */
    ExamResultResponse submit(Integer examId, SubmitExamRequest request, String studentEmail);

    /** Kết quả một bài đã nộp. Chỉ chủ sở hữu bài làm đọc được. */
    ExamResultResponse getResult(Integer submissionId, String studentEmail);

    /**
     * Quét và nộp tự động mọi phiên đã quá ExpiresAt.
     * Dùng cho trường hợp học sinh đóng máy / mất mạng luôn tới hết giờ, khi đó
     * không còn request nào từ client để kích hoạt việc nộp bài.
     *
     * @return số bài vừa được nộp tự động
     */
    int autoSubmitExpiredSessions();

    /**
     * Đánh dấu AtRiskStatus cho các phiên đang làm mà heartbeat đã trễ quá
     * {@code silenceSeconds} giây.
     *
     * @return số phiên vừa bị đánh dấu
     */
    int flagDisconnectedSessions(long silenceSeconds);

    /** Lịch sử các bài đã làm của học sinh. */
    List<ExamResultResponse> getHistory(String studentEmail);
}
