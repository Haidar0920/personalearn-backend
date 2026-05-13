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

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${anthropic.model:claude-3-5-haiku-20241022}")
    private String anthropicModel;

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final OkHttpClient httpClient = new OkHttpClient();

    @Transactional
    public String chat(UUID employeeId, UUID materialId, String userMessage,
                       UUID userId, String chatType) throws Exception {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Material material = (materialId != null)
                ? materialRepository.findById(materialId).orElse(null)
                : null;

        OCEANProfile oceanProfile = oceanProfileService.getOrCreate(employee);
        String llmRules = oceanProfile.getLlmRules();

        // Load conversation history
        List<AIMessage> history = (materialId != null)
                ? aiMessageRepository.findByEmployeeIdAndMaterialIdOrderByCreatedAtAsc(employeeId, materialId)
                : aiMessageRepository.findByUserIdAndChatTypeOrderByCreatedAtAsc(userId, chatType);

        // Build messages for Anthropic (no system role in messages array)
        List<Map<String, String>> messages = new ArrayList<>();
        int startIdx = Math.max(0, history.size() - 20);
        for (int i = startIdx; i < history.size(); i++) {
            AIMessage msg = history.get(i);
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
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

        // Call Claude
        String systemPrompt = buildSystemPrompt(employee, material, llmRules, oceanProfile.getLearningProfile());
        String assistantReply = callClaude(systemPrompt, messages);

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

        // Re-assess OCEAN every 10 turns
        long totalTurns = aiMessageRepository.countByEmployeeId(employeeId);
        if (totalTurns % 10 == 0 && totalTurns > 0) {
            updateOceanFromConversation(employee, oceanProfile, history);
        }

        return assistantReply;
    }

    private String buildSystemPrompt(Employee employee, Material material,
                                     String llmRules, String learningProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ты — AI-наставник платформы PersonaLearn для обучения сотрудников продажам.\n\n");
        sb.append("Сотрудник: ").append(employee.getName())
          .append(", должность: ").append(employee.getPosition() != null ? employee.getPosition() : "не указана")
          .append(".\n\n");

        if (material != null) {
            sb.append("Тема обучения: ").append(material.getTitle()).append(".\n");
            if (material.getDescription() != null)
                sb.append("Описание: ").append(material.getDescription()).append("\n");
            if (material.getGoal() != null)
                sb.append("Цель обучения: ").append(material.getGoal()).append("\n");
            sb.append("\n");
        }

        sb.append("Профиль обучения сотрудника: ").append(learningProfile).append(".\n");
        sb.append("Правила адаптации контента (JSON): ").append(llmRules).append("\n\n");
        sb.append("""
            Строго следуй правилам адаптации:
            - content_order: concept-first = начинай с теории; instruction-first = сразу к действиям
            - content_length: short = 2-3 предложения; medium = абзац; detailed = подробно с примерами
            - feedback_tone: supportive = тёплый поддерживающий тон; direct = прямой и конкретный
            - check_frequency: very-high = проверяй понимание каждые 1-2 ответа; medium = каждые 4-5
            - difficulty_progression: gradual = постепенно усложняй; fast = сразу высокий темп

            Веди диалог на русском языке.
            Оценивай ответы сотрудника по шкале 1-10 когда он отвечает на вопросы.
            Давай конструктивную обратную связь согласно feedback_tone.
            Задавай проверочные вопросы по материалу согласно check_frequency.
            """);

        return sb.toString();
    }

    private String callClaude(String systemPrompt, List<Map<String, String>> messages) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", anthropicModel,
                "max_tokens", 1024,
                "system", systemPrompt,
                "messages", messages
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(ANTHROPIC_API_URL)
                .addHeader("x-api-key", anthropicApiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestJson, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "unknown";
                log.error("Anthropic API error: {} - {}", response.code(), errBody);
                throw new RuntimeException("AI service error: " + response.code());
            }
            String responseJson = response.body().string();
            JsonNode root = objectMapper.readTree(responseJson);
            return root.path("content").get(0).path("text").asText();
        }
    }

    private void updateOceanFromConversation(Employee employee, OCEANProfile profile,
                                              List<AIMessage> history) {
        try {
            String conversationText = history.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .map(AIMessage::getContent)
                    .reduce("", (a, b) -> a + "\n" + b);

            String assessmentPrompt = """
                Analyze these learner responses and assess their OCEAN personality traits for adaptive learning.
                Return ONLY a valid JSON object, nothing else:
                {
                  "o_level": "low|medium|high",
                  "c_level": "low|medium|high",
                  "e_level": "low|medium|high",
                  "a_level": "low|medium|high",
                  "n_level": "low|medium|high"
                }
                Learner responses:
                """ + conversationText;

            String result = callClaude("You are a psychometric assessment AI. Respond only with valid JSON.",
                    List.of(Map.of("role", "user", "content", assessmentPrompt)));

            // Extract JSON from response (Claude may add markdown)
            String json = result.replaceAll("```json|```", "").trim();
            JsonNode jsonNode = objectMapper.readTree(json);

            oceanProfileService.updateLevels(
                    employee.getId(),
                    jsonNode.path("o_level").asText("medium"),
                    jsonNode.path("c_level").asText("medium"),
                    jsonNode.path("e_level").asText("medium"),
                    jsonNode.path("a_level").asText("medium"),
                    jsonNode.path("n_level").asText("low"),
                    history.size()
            );
            log.info("OCEAN profile updated for employee {}", employee.getId());
        } catch (Exception e) {
            log.warn("Failed to update OCEAN profile: {}", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> onboardingChat(Employee employee, String userMessage) throws Exception {
        String chatType = "onboarding";
        UUID userId = employee.getUserId();

        // Load history for this onboarding chat
        List<AIMessage> history = aiMessageRepository.findByUserIdAndChatTypeOrderByCreatedAtAsc(userId, chatType);

        // Build messages for Claude
        List<Map<String, String>> messages = new ArrayList<>();
        int startIdx = Math.max(0, history.size() - 20);
        for (int i = startIdx; i < history.size(); i++) {
            AIMessage msg = history.get(i);
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        // Save user message
        AIMessage userMsg = AIMessage.builder()
                .employee(employee)
                .role("user")
                .content(userMessage)
                .chatType(chatType)
                .userId(userId)
                .build();
        aiMessageRepository.save(userMsg);

        // Count user turns
        long userTurns = history.stream().filter(m -> "user".equals(m.getRole())).count() + 1;

        boolean completed = false;

        // After 7 user turns, force OCEAN update and mark onboarding complete
        if (userTurns >= 7) {
            OCEANProfile oceanProfile = oceanProfileService.getOrCreate(employee);
            updateOceanFromConversation(employee, oceanProfile, history);
            employee.setOnboardingCompleted(true);
            employeeRepository.save(employee);
            completed = true;
        }

        String systemPrompt = """
                Ты — AI-ассистент платформы обучения PersonaLearn. Познакомься с сотрудником и пойми его стиль обучения через разговор.
                Задай ровно 7 вопросов по одному. Вопросы должны помочь понять: предпочитает теорию или практику сначала, комфорт с неопределённостью, нужна ли структура и планы, реакция на ошибки, самостоятельность vs командная работа, важность поддержки и обратной связи, предпочитаемый темп.
                Будь дружелюбным, задавай по одному вопросу за раз. Не упоминай OCEAN. Общайся на русском языке.
                После 7-го ответа сотрудника скажи что онбординг завершён и персональный план обучения готов.
                """;

        String assistantReply = callClaude(systemPrompt, messages);

        // Save assistant message
        AIMessage assistantMsg = AIMessage.builder()
                .employee(employee)
                .role("assistant")
                .content(assistantReply)
                .chatType(chatType)
                .userId(userId)
                .build();
        aiMessageRepository.save(assistantMsg);

        // Get learning profile from OCEAN
        OCEANProfile oceanProfile = oceanProfileService.getOrCreate(employee);
        String learningProfile = oceanProfile.getLearningProfile();

        return Map.of(
                "reply", assistantReply,
                "completed", completed,
                "learningProfile", learningProfile != null ? learningProfile : "STRUCTURED_GUIDE"
        );
    }

    public List<AIMessage> getHistory(UUID employeeId, UUID materialId,
                                       UUID userId, String chatType) {
        if (materialId != null) {
            return aiMessageRepository.findByEmployeeIdAndMaterialIdOrderByCreatedAtAsc(employeeId, materialId);
        }
        return aiMessageRepository.findByUserIdAndChatTypeOrderByCreatedAtAsc(userId, chatType);
    }
}
