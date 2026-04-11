package com.mysite.sbb.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GeminiService {

    private static final String SYSTEM_PROMPT =
            "You are the A2C assistant for an Aion 2 community site. Answer in Korean, keep it practical, and be concise.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String apiBaseUrl;

    public GeminiService(RestTemplateBuilder restTemplateBuilder,
                         ObjectMapper objectMapper,
                         @Value("${gemini.api.key:}") String apiKey,
                         @Value("${gemini.api.model:gemini-2.5-flash}") String model,
                         @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta/models}") String apiBaseUrl) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.apiBaseUrl = apiBaseUrl;
    }

    public String ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("질문을 입력해주세요.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini API 키가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.add("x-goog-api-key", apiKey.trim());

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(
                                Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", question.trim())))));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(buildApiUrl(), HttpMethod.POST, request, String.class);
            return extractContent(readResponseBody(response.getBody()));
        } catch (HttpStatusCodeException exception) {
            throw translateApiException(exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (ResourceAccessException exception) {
            throw new GeminiRequestException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini 서버에 연결하지 못했습니다. 네트워크 연결과 방화벽 설정을 확인해주세요.",
                    exception);
        }
    }

    private String buildApiUrl() {
        return apiBaseUrl + "/" + model + ":generateContent";
    }

    private JsonNode readResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new GeminiRequestException(HttpStatus.BAD_GATEWAY, "Gemini 응답이 비어 있습니다.");
        }

        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new GeminiRequestException(HttpStatus.BAD_GATEWAY, "Gemini 응답 형식을 읽지 못했습니다.", exception);
        }
    }

    private String extractContent(JsonNode responseBody) {
        String text = joinCandidateText(responseBody.path("candidates"));
        if (!text.isEmpty()) {
            return text;
        }

        String blockReason = responseBody.path("promptFeedback").path("blockReason").asText("").trim();
        if (!blockReason.isEmpty()) {
            throw new GeminiRequestException(HttpStatus.BAD_GATEWAY,
                    "Gemini가 요청을 차단했습니다. 사유: " + blockReason);
        }

        throw new GeminiRequestException(HttpStatus.BAD_GATEWAY, "AI 응답 내용이 비어 있습니다.");
    }

    private String joinCandidateText(JsonNode candidatesNode) {
        if (!candidatesNode.isArray()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode candidateNode : candidatesNode) {
            JsonNode partsNode = candidateNode.path("content").path("parts");
            if (!partsNode.isArray()) {
                continue;
            }

            for (JsonNode partNode : partsNode) {
                String text = partNode.path("text").asText("").trim();
                if (text.isEmpty()) {
                    continue;
                }

                if (builder.length() > 0) {
                    builder.append(System.lineSeparator()).append(System.lineSeparator());
                }
                builder.append(text);
            }
        }

        return builder.toString();
    }

    private GeminiRequestException translateApiException(HttpStatusCode statusCode, String responseBody) {
        String detailMessage = extractErrorMessage(responseBody);

        String message;
        switch (statusCode.value()) {
            case 400 -> message = "Gemini 요청 형식이 올바르지 않습니다. 모델명과 요청 데이터를 확인해주세요.";
            case 401, 403 -> message = "Gemini API 인증에 실패했습니다. API 키와 프로젝트 권한을 확인해주세요.";
            case 404 -> message = "Gemini API 경로 또는 모델 설정을 확인해주세요.";
            case 429 -> message = "Gemini API 사용량 또는 요청 속도 제한에 걸렸습니다. 잠시 후 다시 시도해주세요.";
            default -> {
                if (statusCode.is5xxServerError()) {
                    message = "Gemini 서버에서 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
                } else {
                    message = "Gemini API 요청이 실패했습니다.";
                }
            }
        }

        if (!detailMessage.isBlank()) {
            message = message + " 상세: " + detailMessage;
        }

        return new GeminiRequestException(HttpStatus.BAD_GATEWAY, message);
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode errorNode = rootNode.path("error");
            String message = errorNode.path("message").asText("").trim();
            if (!message.isEmpty()) {
                return message;
            }
        } catch (JsonProcessingException ignored) {
        }

        return responseBody.replaceAll("\\s+", " ").trim();
    }
}
