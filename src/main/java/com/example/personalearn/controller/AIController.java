package com.example.personalearn.controller;

import com.example.personalearn.dto.request.ChatRequest;
import com.example.personalearn.dto.response.ChatMessageResponse;
import com.example.personalearn.entity.AIMessage;
import com.example.personalearn.service.AIService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AIController {

    private final AIService aiService;

    /**
     * POST /ai/chat
     * Send a message and get AI response.
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest req) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            String chatType = (req.chatType() != null) ? req.chatType() : "employee_chat";

            String reply = aiService.chat(
                    req.employeeId(),
                    req.materialId(),
                    req.message(),
                    userId,
                    chatType
            );

            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "AI service unavailable. Please try again."));
        }
    }

    /**
     * GET /ai/history?employeeId=...&materialId=...&chatType=...
     * Get conversation history.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatMessageResponse>> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID employeeId,
            @RequestParam(required = false) UUID materialId,
            @RequestParam(defaultValue = "employee_chat") String chatType) {

        UUID userId = UUID.fromString(jwt.getSubject());
        List<AIMessage> messages = aiService.getHistory(employeeId, materialId, userId, chatType);

        List<ChatMessageResponse> response = messages.stream()
                .map(m -> ChatMessageResponse.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }
}
