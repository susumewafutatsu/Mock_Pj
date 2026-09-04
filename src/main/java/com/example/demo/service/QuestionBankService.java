package com.example.demo.service;

import com.example.demo.dto.request.QuestionBankCreateRequest;
import com.example.demo.dto.response.QuestionBankResponse;

import java.util.List;

public interface QuestionBankService {

    /** Danh sách ngân hàng của giáo viên đang đăng nhập, kèm số câu hỏi. */
    List<QuestionBankResponse> listMine(String teacherEmail);

    QuestionBankResponse create(QuestionBankCreateRequest request, String teacherEmail);
}
