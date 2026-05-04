package com.example.personalearn.service;

import com.example.personalearn.entity.AIMessage;
import com.example.personalearn.entity.Employee;
import com.example.personalearn.entity.Material;
import com.example.personalearn.entity.OCEANProfile;
import com.example.personalearn.repository.AIMessageRepository;
import com.example.personalearn.repository.EmployeeRepository;
import com.example.personalearn.repository.MaterialRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final AIMessageRepository aiMessageRepository;
    private final EmployeeRepository employeeRepository;
    private final MaterialRepository materialRepository;
    private final OCEANProfileService oceanProfileService;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    private final OkHttpClient httpClient = new OkHttpClient();

    /**
     * Send a message and get AI response.
     * Adapts tone/format based on employee's OCEAN profile.
     *
     * @param employeeId employee UUID
     * @param materialId optional material context (null for general chat)
     * @param userMessage user's message text
     * @param userId Supabase user UUID from JWT
     * @param chatType "employee_chat" | "admin_chat"
     */
    @Transactional
    public String chat(UUID employeeId, UUID materialId, String userMessage,
                       UUID userId, String chatType) throws Exception {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Material material = (materialId != null)
                ? materialRepository.findById(materialId).orElse(null)
                : null;

        // Get OCEAN profile and LLM rules
        OCEANProfile oceanProfile = oceanProfileService.getOrCreate(employee);
        String llmRules = oceanProfile.getLlmRules();

        // Load conversation history
        List<AIMessage> history = (materialId != null)
                ? aiMessageRepository.findByEmployeeIdAndMaterialIdOrderByCreatedAtAsc(employeeId, materialId)
                : aiMessageRepository.findByUserIdAndChatTypeOrderByCreatedAtAsc(userId, chatType);

        // Build messages for OpenAI
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(employee, material, llmRules, oceanProfile.getLearningProfile())));

        // Add history (last 20 turns to stay within context)
        int startIdx = Math.max(0, history.size() - 20);
        for (int i = startIdx; i < history.size(); i++) {
            AIMessage msg = history.get(i);
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        // Add current user message
        messages.add(Map.of("role", "user", "content", userMessage));

        // Save user message
        AIMessage userMsg = AIMessage.builder()
                .employee(employee)
                .material(material)
                .role("user")
                .content(userMessage)
                .chatType(chatType)
                .userId(userId)
                .build();
        aiMessageRepository.save(userMsg);

        // Call OpenAI
        String assistantReply = callOpenAI(messages);

        // Save assistant message
        AIMessage assistantMsg = AIMessage.builder()
                .employee(employee)
                .material(material)
                .role("assistant")
                .content(assistantReply)
                .chatType(chatType)
                .userId(userId)
                .build();
        aiMessageRepository.save(assistantMsg);

        // Update OCEAN profile assessment turn count
        long totalTurns = aiMessageRepository.countByEmployeeId(employeeId);
        if (totalTurns % 10 == 0 && totalTurns > 0) {
            updateOceanFromConversation(employee, oceanProfile, history);
        }

        return assistantReply;
    }

    /**
     * Builds system prompt injecting OCEAN rules for adaptive content delivery.
     */
    private String buildSystemPrompt(Employee employee, Material material,
                                     String llmRules, String learningProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ты — AI-наставник платформы PersonaLearn для обучения сотрудников продажам.\n\n");
        sb.append("Сотрудник: ").append(employee.getName())
          .append(", должность: ").append(employee.getPosition()).append(".\n\n");

        if (material != null) {
            sb.append("Тема обучения: ").append(material.getTitle()).append(".\n");
            if (material.getDescription() != null) {
                sb.append("Описание: ").append(material.getDescription()).append("\n");
            }
            if (material.getGoal() != null) {
                sb.append("Цель обучения: ").append(material.getGoal()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Профиль обучения: ").append(learningProfile).append(".\n");
        sb.append("Правила адаптации контента (JSON): ").append(llmRules).append("\n\n");
        sb.append("""
            Следуй правилам адаптации строго:
            - content_order: concept-first = начинай с теории; instruction-first = сразу к действиям
            - content_length: short = 2-3 предложения; medium = абзац; detailed = подробно
            - feedback_tone: supportive = поддерживающий тон; direct = прямой и конкретный
            - check_frequency: very-high = проверяй каждые 1-2 обмена; medium = каждые 4-5
            - difficulty_progression: gradual = постепенно усложняй; fast = сразу сложные задачи

            Веди диалог по-русски. Оценивай ответы по шкале 1-10, давай конструктивную обратную связь.
            Задавай уточняющие вопросы по теме материала.
            """);

        return sb.toString();
    }

    /**
     * Calls OpenAI Chat Completions API.
     */
    private String callOpenAI(List<Map<String, String>> messages) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", openaiModel,
                "messages", messages,
                "max_tokens", 1000,
                "temperature", 0.7
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(openaiBaseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + openaiApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestJson, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "unknown";
                log.error("OpenAI API error: {} - {}", response.code(), errBody);
                throw new RuntimeException("OpenAI API error: " + response.code());
            }
            String responseJson = response.body().string();
            JsonNode root = objectMapper.readTree(responseJson);
            return root.path("choices").get(0).path("message").path("content").asText();
        }
    }

    /**
     * Periodically re-assess OCEAN levels from conversation history.
     * Uses OpenAI to analyze conversation patterns.
     */
    private void updateOceanFromConversation(Employee employee, OCEANProfile profile,
                                              List<AIMessage> history) {
        try {
            String conversationText = history.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .map(AIMessage::getContent)
                    .reduce("", (a, b) -> a + "\n" + b);

            String assessmentPrompt = """
                Analyze these learner responses and assess their OCEAN personality traits.
                Return ONLY a valid JSON object with no other text:
                {
                  "o_level": "low|medium|high",
                  "c_level": "low|medium|high",
                  "e_level": "low|medium|high",
                  "a_level": "low|medium|high",
                  "n_level": "low|medium|high"
                }

                Learner responses:
                """ + conversationText;

            List<Map<String, String>> assessMessages = List.of(
                    Map.of("role", "user", "content", assessmentPrompt)
            );

            String result = callOpenAI(assessMessages);
            JsonNode json = objectMapper.readTree(result);

            oceanProfileService.updateLevels(
                    employee.getId(),
                    json.path("o_level").asText("medium"),
                    json.path("c_level").asText("medium"),
                    json.path("e_level").asText("medium"),
                    json.path("a_level").asText("medium"),
                    json.path("n_level").asText("low"),
                    history.size()
            );
            log.info("OCEAN profile updated for employee {}", employee.getId());
        } catch (Exception e) {
            log.warn("Failed to update OCEAN profile: {}", e.getMessage());
        }
    }

    /**
     * Get chat history for display.
     */
    public List<AIMessage> getHistory(UUID employeeId, UUID materialId,
                                       UUID userId, String chatType) {
        if (materialId != null) {
            return aiMessageRepository.findByEmployeeIdAndMaterialIdOrderByCreatedAtAsc(employeeId, materialId);
        }
        return aiMessageRepository.findByUserIdAndChatTypeOrderByCreatedAtAsc(userId, chatType);
    }
}
