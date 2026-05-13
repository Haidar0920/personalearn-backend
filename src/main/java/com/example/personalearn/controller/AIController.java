package com.example.personalearn.controller;

import com.example.personalearn.dto.request.ChatRequest;
import com.example.personalearn.dto.response.ChatMessageResponse;
import com.example.personalearn.entity.AIMessage;
import com.example.personalearn.entity.Employee;
import com.example.personalearn.repository.EmployeeRepository;
import com.example.personalearn.service.AIService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final EmployeeRepository employeeRepository;

    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChatRequest req) {
        if (jwt == null) return ResponseEntity.status(401).build();
        try {
            UUID userId = UUID.fromString(jwt.getSubject());

            // Caller can be: manager (createdBy) or the employee themselves (userId)
            Employee emp = employeeRepository.findById(req.employeeId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            boolean isOwner = emp.getCreatedBy().equals(userId);
            boolean isSelf = userId.equals(emp.getUserId());
            if (!isOwner && !isSelf) {
                return ResponseEntity.status(403).build();
            }

            String chatType = (req.chatType() != null) ? req.chatType() : "employee_chat";
            String reply = aiService.chat(req.employeeId(), req.materialId(), req.message(), userId, chatType);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "AI service unavailable. Please try again."));
        }
    }

    @PostMapping("/onboarding")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> onboarding(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {
        if (jwt == null) return ResponseEntity.status(401).build();
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            Employee employee = employeeRepository.findByUserId(userId)
                    .orElse(null);
            if (employee == null) {
                return ResponseEntity.notFound().build();
            }
            String message = body.get("message");
            Map<String, Object> result = aiService.onboardingChat(employee, message);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Onboarding chat error: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "AI service unavailable. Please try again."));
        }
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageResponse>> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID employeeId,
            @RequestParam(required = false) UUID materialId,
            @RequestParam(defaultValue = "employee_chat") String chatType) {
        if (jwt == null) return ResponseEntity.status(401).build();

        UUID userId = UUID.fromString(jwt.getSubject());

        // Caller can be: manager (createdBy) or the employee themselves (userId)
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        boolean isOwner = emp.getCreatedBy().equals(userId);
        boolean isSelf = userId.equals(emp.getUserId());
        if (!isOwner && !isSelf) {
            return ResponseEntity.status(403).build();
        }

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
