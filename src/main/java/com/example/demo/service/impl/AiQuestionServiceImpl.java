package com.example.demo.service.impl;

import com.example.demo.domain.enums.QuestionType;
import com.example.demo.dto.request.AiGenerateRequest;
import com.example.demo.dto.request.AnswerPayload;
import com.example.demo.dto.request.QuestionCreateRequest;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.AiQuestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/** Gọi Gemini để sinh câu hỏi trắc nghiệm từ văn bản đã trích xuất (PDF/Word). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionServiceImpl implements AiQuestionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-flash-latest}")
    private String model;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    @Value("${gemini.max-questions-per-request:20}")
    private int maxQuestionsPerRequest;

    @Override
    public List<QuestionCreateRequest> generateFromText(AiGenerateRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("Chưa cấu hình GEMINI_API_KEY trên máy chủ");
        }
        int count = Math.min(request.getQuestionCount(), maxQuestionsPerRequest);

        String prompt = buildPrompt(request, count);
        JsonNode root = callGemini(prompt);
        String generatedJson = extractGeneratedText(root);
        return parseQuestions(generatedJson);
    }

    private String buildPrompt(AiGenerateRequest request, int count) {
        return """
                Bạn là trợ lý ra đề thi JLPT (tiếng Nhật). Dựa vào ĐOẠN VĂN BẢN sau, hãy sinh ra %d câu hỏi
                trắc nghiệm (mỗi câu 4 phương án, chỉ 1 đáp án đúng). Chỉ trả về JSON, không giải thích thêm,
                đúng định dạng mảng sau:
                [
                  {
                    "content": "nội dung câu hỏi",
                    "questionType": "MULTIPLE_CHOICE",
                    "difficultyLevel": %s,
                    "skill": %s,
                    "explanation": "giải thích ngắn vì sao đáp án đó đúng",
                    "answers": [
                      { "answerContent": "phương án 1", "correct": true },
                      { "answerContent": "phương án 2", "correct": false },
                      { "answerContent": "phương án 3", "correct": false },
                      { "answerContent": "phương án 4", "correct": false }
                    ]
                  }
                ]

                ĐOẠN VĂN BẢN:
                %s
                """.formatted(
                count,
                request.getDifficultyLevel() == null ? "null" : request.getDifficultyLevel(),
                request.getSkill() == null ? "null" : "\"" + request.getSkill().name() + "\"",
                request.getText());
    }

    private JsonNode callGemini(String prompt) {
        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/v1beta/models/" + model + ":generateContent")
                .queryParam("key", apiKey)
                .toUriString();

        var body = objectMapper.createObjectNode();
        var contents = body.putArray("contents");
        var part = contents.addObject().putArray("parts").addObject();
        part.put("text", prompt);
        var generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            JsonNode response = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), JsonNode.class);
            if (response == null) {
                throw new BusinessException("Gemini không trả về dữ liệu");
            }
            return response;
        } catch (HttpStatusCodeException e) {
            log.error("Gemini trả lỗi {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            int status = e.getStatusCode().value();
            if (status == 503 || status == 429) {
                throw new BusinessException("Gemini đang quá tải hoặc hết lượt gọi, thử lại sau ít phút");
            }
            if (status == 400 || status == 401 || status == 403) {
                throw new BusinessException("GEMINI_API_KEY không hợp lệ hoặc không có quyền");
            }
            if (status == 404) {
                throw new BusinessException("Model Gemini \"" + model + "\" không tồn tại, kiểm tra GEMINI_MODEL");
            }
            throw new BusinessException("Gemini trả lỗi " + status + ", thử lại sau");
        } catch (RestClientException e) {
            log.error("Lỗi gọi Gemini API", e);
            throw new BusinessException("Không kết nối được tới Gemini, kiểm tra mạng của máy chủ");
        }
    }

    private String extractGeneratedText(JsonNode root) {
        JsonNode textNode = root.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new BusinessException("Gemini không sinh được câu hỏi từ nội dung này");
        }
        return textNode.asText();
    }

    private List<QuestionCreateRequest> parseQuestions(String json) {
        JsonNode arrayNode;
        try {
            arrayNode = objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Gemini trả về JSON không hợp lệ: {}", json, e);
            throw new BusinessException("Gemini trả về dữ liệu không đúng định dạng");
        }
        if (!arrayNode.isArray() || arrayNode.isEmpty()) {
            throw new BusinessException("Gemini không sinh được câu hỏi nào");
        }

        List<QuestionCreateRequest> result = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            QuestionCreateRequest q = new QuestionCreateRequest();
            q.setContent(node.path("content").asText(""));
            q.setQuestionType(QuestionType.MULTIPLE_CHOICE);
            if (node.hasNonNull("difficultyLevel")) {
                q.setDifficultyLevel(node.path("difficultyLevel").asInt());
            }
            if (node.hasNonNull("skill")) {
                try {
                    q.setSkill(com.example.demo.domain.enums.JlptSkill.valueOf(node.path("skill").asText()));
                } catch (IllegalArgumentException ignored) {
                    // Gemini trả sai tên skill: bỏ trống, giáo viên tự chọn lại khi duyệt.
                }
            }
            q.setExplanation(node.path("explanation").asText(null));
            q.setAiGenerated(true);

            List<AnswerPayload> answers = new ArrayList<>();
            for (JsonNode a : node.path("answers")) {
                AnswerPayload payload = new AnswerPayload();
                payload.setAnswerContent(a.path("answerContent").asText(""));
                payload.setCorrect(a.path("correct").asBoolean(false));
                answers.add(payload);
            }
            q.setAnswers(answers);
            result.add(q);
        }
        return result;
    }
}
