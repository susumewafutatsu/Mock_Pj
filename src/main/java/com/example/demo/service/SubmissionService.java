package com.example.demo.service;

import com.example.demo.dto.request.SaveAnswerRequest;
import com.example.demo.dto.request.SaveAnswersBatchRequest;
import com.example.demo.dto.request.SubmitExamRequest;
import com.example.demo.dto.response.AnswerSavedResponse;
import com.example.demo.dto.response.AnswersBatchSavedResponse;
import com.example.demo.dto.response.ExamResultResponse;
import com.example.demo.dto.response.ExamSessionResponse;
import com.example.demo.dto.response.HeartbeatResponse;

import java.util.List;

/** Vòng đời một phiên làm bài của thí sinh, từ lúc bắt đầu tới lúc có điểm. */
public interface SubmissionService {

    /** Vào phòng thi: tạo phiên nếu chưa có, trả lại phiên đang dở nếu đã có. */
    ExamSessionResponse startOrResume(Integer examId, String studentEmail);

    /** Đọc lại phiên đang dở mà không tạo mới — dùng cho luồng "khôi phục sau khi mất kết nối". */
    ExamSessionResponse getSession(Integer examId, String studentEmail);

    /** Autosave một câu trả lời (upsert theo SubmissionID + QuestionID). */
    AnswerSavedResponse saveAnswer(Integer examId, SaveAnswerRequest request, String studentEmail);

    /** Lưu một lô đáp án client đã gom sẵn, trong một transaction. */
    AnswersBatchSavedResponse saveAnswers(Integer examId, SaveAnswersBatchRequest request, String studentEmail);

    /** Nhịp sống của client, 15-30 giây một lần. */
    HeartbeatResponse heartbeat(Integer examId, String studentEmail);

    /** Thí sinh chủ động nộp bài. Chấm ngay các câu trắc nghiệm. */
    ExamResultResponse submit(Integer examId, SubmitExamRequest request, String studentEmail);

    /** Kết quả một bài đã nộp. Chỉ chủ sở hữu bài làm đọc được. */
    ExamResultResponse getResult(Integer submissionId, String studentEmail);

    /** Bài làm đầy đủ của một lượt nộp, dựng cho NGƯỜI RA ĐỀ xem. */
    ExamResultResponse getPaperForReview(Integer submissionId);

    /** Ghi một lượt nghe file audio của câu. */
    com.example.demo.dto.response.AudioPlayResponse recordAudioPlay(Integer examId, Integer questionId,
                                                                    String studentEmail);

    /** Quét và nộp tự động mọi phiên đã quá ExpiresAt. */
    int autoSubmitExpiredSessions();

    /** Đánh dấu AtRiskStatus cho các phiên đang làm mà heartbeat đã trễ quá {@code silenceSeconds} giây. */
    int flagDisconnectedSessions(long silenceSeconds);

    /** Lịch sử các bài đã làm của thí sinh. */
    List<ExamResultResponse> getHistory(String studentEmail);
}
