package com.example.demo.service;

import com.example.demo.dto.request.QuestionBankCreateRequest;
import com.example.demo.dto.response.QuestionBankResponse;

import java.util.List;

/**
 * Ngân hàng câu hỏi thuộc về đúng một giáo viên. Mọi phương thức nhận
 * {@code teacherEmail} lấy từ token và chỉ làm việc trên dữ liệu của người đó.
 */
public interface QuestionBankService {

    /** Danh sách ngân hàng của giáo viên đang đăng nhập, kèm số câu hỏi. */
    List<QuestionBankResponse> listMine(String teacherEmail);

    QuestionBankResponse create(QuestionBankCreateRequest request, String teacherEmail);
}
