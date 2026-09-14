package com.example.demo.controller;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tải file nghe (聴解) lên máy chủ. */
@RestController
@RequestMapping("/api/teacher/media")
@RequiredArgsConstructor
public class MediaController {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "m4a", "wav", "ogg");
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/audio")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAudio(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Chưa chọn file nghe");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("File nghe tối đa 20 MB");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!AUDIO_EXTENSIONS.contains(ext)) {
            throw new BusinessException("Chỉ nhận file âm thanh .mp3, .m4a, .wav hoặc .ogg");
        }

        Path dir = Path.of(uploadDir, "audio").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(name).normalize();
        // Phòng hờ: tên do server sinh nên không thể thoát thư mục.
        if (!target.startsWith(dir)) {
            throw new BusinessException("Tên file không hợp lệ");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tải file nghe lên", Map.of("url", "/media/audio/" + name)));
    }
}
