package com.example.demo.service;

import org.springframework.web.multipart.MultipartFile;

/** Trích văn bản thô từ file PDF hoặc Word (.docx) để làm nguồn sinh câu hỏi. */
public interface DocumentTextExtractorService {

    String extractText(MultipartFile file);
}
