package com.example.demo.service.impl;

import com.example.demo.domain.model.*;
import com.example.demo.dto.request.ExamQuestionSelection;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.*;
import com.example.demo.service.ExamSnapshotService;
import com.example.demo.service.cache.ExamRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamSnapshotServiceImpl implements ExamSnapshotService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamQuestionAnswerRepository snapshotAnswerRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final ExamRedisService examRedis;

    @Override
    @Transactional
    public int attachQuestions(Integer examId, List<ExamQuestionSelection> selections,
                               String teacherEmail) {
        Exam exam = requireOwnedExam(examId, teacherEmail);
        // có 1 lỗi ẩn là trong khoảng thời gian hệ thống check xong 
        // mà có sinh viên nộp bài mà lúc đó giáo viên lại sửa thì sẽ có lỗi
        if (submissionRepository.existsByExamExamId(examId)) {
            throw new BusinessException("Đề đã có học sinh làm bài, không thể thêm câu hỏi");
        }

        int order = (int) examQuestionRepository.countByExam_ExamId(examId);
        int added = 0;

        for (ExamQuestionSelection sel : selections) {
            if (examQuestionRepository
                    .findByExam_ExamIdAndQuestion_QuestionId(examId, sel.getQuestionId())
                    .isPresent()) {
                continue;   // đã có trong đề, giữ nguyên snapshot cũ
            }
            Question source = questionRepository.findById(sel.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy câu hỏi id=" + sel.getQuestionId()));
            if (Boolean.TRUE.equals(source.getIsDeleted())) {
                throw new BusinessException(
                        "Câu hỏi id=" + source.getQuestionId() + " đã bị xoá khỏi ngân hàng");
            }

            ExamQuestion examQuestion = ExamQuestion.builder()
                    .id(new ExamQuestionKey(examId, source.getQuestionId()))
                    .exam(exam)
                    .question(source)
                    .points(sel.getPoints())
                    .questionOrder(sel.getQuestionOrder() != null ? sel.getQuestionOrder() : ++order)
                    .build();
            examQuestion.captureFrom(source);
            examQuestionRepository.save(examQuestion);

            snapshotAnswers(examQuestion, source);
            added++;
        }
        if (added > 0) {
            evictPaperCache(examId);
        }
        return added;
    }

    @Override
    @Transactional
    public void refreshSnapshot(Integer examId, Integer questionId, String teacherEmail) {
        requireOwnedExam(examId, teacherEmail);

        // Mốc không thể vượt qua: một khi có bài làm, snapshot là bằng chứng
        // của điểm số đã chấm. Sửa nó sẽ làm kết quả cũ không giải thích được.
        if (submissionRepository.existsByExamExamId(examId)) {
            throw new BusinessException(
                    "Đề đã có học sinh làm bài. Không thể cập nhật lại nội dung câu hỏi trong đề này. "
                    + "Hãy tạo đề mới nếu cần dùng phiên bản câu hỏi mới nhất.");
        }

        ExamQuestion examQuestion = examQuestionRepository
                .findByExam_ExamIdAndQuestion_QuestionId(examId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Câu hỏi id=" + questionId + " không nằm trong đề id=" + examId));

        Question source = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy câu hỏi id=" + questionId));

        examQuestion.captureFrom(source);
        snapshotAnswerRepository
                .deleteByExamQuestion_Exam_ExamIdAndExamQuestion_Question_QuestionId(examId, questionId);
        snapshotAnswerRepository.flush();
        snapshotAnswers(examQuestion, source);
        evictPaperCache(examId);
    }

    @Override
    @Transactional
    public void detachQuestion(Integer examId, Integer questionId, String teacherEmail) {
        requireOwnedExam(examId, teacherEmail);
        if (submissionRepository.existsByExamExamId(examId)) {
            throw new BusinessException("Đề đã có học sinh làm bài, không thể bỏ câu hỏi");
        }
        ExamQuestion examQuestion = examQuestionRepository
                .findByExam_ExamIdAndQuestion_QuestionId(examId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Câu hỏi id=" + questionId + " không nằm trong đề id=" + examId));
        // ExamQuestionAnswers có FK ON DELETE CASCADE nên snapshot đáp án tự đi theo.
        examQuestionRepository.delete(examQuestion);
        evictPaperCache(examId);
    }

    /**
     * Bỏ bản cache đề thi trong Redis sau khi cấu trúc đề đổi.
     *
     * Chỉ xoá SAU KHI transaction commit. Xoá ngay trong thân method thì có một
     * kẽ hở: cache vừa trống, một học sinh vào phòng thi nạp lại cache từ dữ
     * liệu CŨ (thay đổi chưa commit), rồi transaction mới commit — cache lại sai
     * và lần này không còn ai đi xoá nữa.
     *
     * Ba method sửa đề ở lớp này đều đã chặn khi đề có bài làm, nên trên thực tế
     * hiếm khi có cache để xoá. Vẫn gọi vì đây là nơi duy nhất biết đề vừa đổi:
     * ràng buộc kia là quy tắc nghiệp vụ, có thể nới ra sau, còn cache sai thì
     * học sinh làm nhầm đề.
     */
    private void evictPaperCache(Integer examId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            examRedis.evictPaper(examId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                examRedis.evictPaper(examId);
            }
        });
    }

    /** Sao chép toàn bộ đáp án của câu hỏi vào bảng snapshot của đề thi. */
    private void snapshotAnswers(ExamQuestion examQuestion, Question source) {
        List<Answer> liveAnswers = answerRepository.findByQuestion_QuestionId(source.getQuestionId());
        int order = 0;
        List<ExamQuestionAnswer> snapshots = new java.util.ArrayList<>();
        for (Answer a : liveAnswers) {
            snapshots.add(ExamQuestionAnswer.builder()
                    .examQuestion(examQuestion)
                    .originalAnswer(a)
                    .answerContent(a.getAnswerContent())
                    .isCorrect(Boolean.TRUE.equals(a.getIsCorrect()))
                    .answerOrder(++order)
                    .build());
        }
        snapshotAnswerRepository.saveAll(snapshots);
    }

    private Exam requireOwnedExam(Integer examId, String teacherEmail) {
        String teacherId = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + teacherEmail))
                .getUserId();

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId));

        if (!exam.getCreatedBy().getUserId().equals(teacherId)) {
            throw new ResourceNotFoundException("Không tìm thấy đề thi id=" + examId);
        }
        return exam;
    }
}
