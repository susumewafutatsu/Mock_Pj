package com.example.demo.service.impl;

import com.example.demo.exception.BusinessException;
import com.example.demo.service.DocumentTextExtractorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class DocumentTextExtractorServiceImpl implements DocumentTextExtractorService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx");
    private static final int MIN_TEXT_LENGTH = 20;

    @Override
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Chưa chọn file để import");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Chỉ nhận file .pdf hoặc .docx");
        }

        String text;
        try (InputStream in = file.getInputStream()) {
            text = "pdf".equals(extension) ? extractPdf(in) : extractDocx(in);
        } catch (IOException e) {
            log.error("Không đọc được file import", e);
            throw new BusinessException("Không đọc được file, có thể file bị hỏng");
        }

        if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
            throw new BusinessException(
                    "Không trích được nội dung văn bản từ file này (có thể là bản scan ảnh, chưa hỗ trợ)");
        }
        return text.trim();
    }

    private String extractPdf(InputStream in) throws IOException {
        try (PDDocument document = Loader.loadPDF(in.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(InputStream in) throws IOException {
        try (XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
