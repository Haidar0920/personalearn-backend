package com.example.personalearn.service;

import com.example.personalearn.entity.AIMessage;
import com.example.personalearn.entity.Employee;
import com.example.personalearn.entity.Material;
import com.example.personalearn.entity.OCEANProfile;
import com.example.personalearn.repository.AIMessageRepository;
import com.example.personalearn.repository.EmployeeRepository;
import com.example.personalearn.repository.MaterialRepository;
import com.example.personalearn.repository.OCEANProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final AIMessageRepository aiMessageRepository;
    private final EmployeeRepository employeeRepository;
    private final MaterialRepository materialRepository;
    private final OCEANProfileService oceanProfileService;
    private final OCEANProfileRepository oceanProfileRepository;
    private final OceanQuestionBank oceanQuestionBank;
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
            if (material.getContent() != null && !material.getContent().isBlank()) {
                // Truncate to 6000 chars to fit in context
                String contentSnippet = material.getContent().length() > 6000
                    ? material.getContent().substring(0, 6000) + "\n[...текст обрезан...]"
                    : material.getContent();
                sb.append("Полное содержание материала:\n").append(contentSnippet).append("\n");
            }
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

        // Step 1: Get or create OCEAN profile
        OCEANProfile oceanProfile = oceanProfileService.getOrCreate(employee);

        // Step 2: Load assessment state
        Map<String, Double> confidence = new HashMap<>(Map.of("O", 0.0, "C", 0.0, "E", 0.0, "A", 0.0, "N", 0.0));
        Set<String> askedIds = new LinkedHashSet<>();
        int questionCount = 0;

        if (oceanProfile.getAssessmentState() != null && !oceanProfile.getAssessmentState().isBlank()) {
            try {
                JsonNode stateNode = objectMapper.readTree(oceanProfile.getAssessmentState());
                if (stateNode.has("confidence")) {
                    JsonNode confNode = stateNode.get("confidence");
                    confNode.fields().forEachRemaining(e -> confidence.put(e.getKey(), e.getValue().asDouble(0.0)));
                }
                if (stateNode.has("askedIds")) {
                    stateNode.get("askedIds").forEach(n -> askedIds.add(n.asText()));
                }
                if (stateNode.has("questionCount")) {
                    questionCount = stateNode.get("questionCount").asInt(0);
                }
            } catch (Exception e) {
                log.warn("Failed to parse assessment state, starting fresh: {}", e.getMessage());
            }
        }

        // Step 3: First message — send initial question
        if (questionCount == 0 && "start".equals(userMessage)) {
            OceanQuestionBank.Question firstQuestion = oceanQuestionBank.selectNext(confidence, askedIds)
                    .orElseThrow(() -> new RuntimeException("No questions available"));

            // Save assistant message with first question
            AIMessage assistantMsg = AIMessage.builder()
                    .employee(employee)
                    .role("assistant")
                    .content("Привет! Я помогу настроить обучение под тебя. " + firstQuestion.text())
                    .chatType(chatType)
                    .userId(userId)
                    .build();
            aiMessageRepository.save(assistantMsg);

            // Save state with the question marked as pending (not yet answered)
            // We track it separately so we know which question was asked
            oceanProfile.setAssessmentState(serializeState(confidence, askedIds, questionCount));
            oceanProfileRepository.save(oceanProfile);

            return new HashMap<>(Map.of(
                    "reply", "Привет! Я помогу настроить обучение под тебя. " + firstQuestion.text(),
                    "completed", false,
                    "learningProfile", "STRUCTURED_GUIDE"
            ));
        }

        // Step 4: User answered a question — process the answer
        // Load history to find the last assistant question
        List<AIMessage> history = aiMessageRepository.findByUserIdAndChatTypeOrderByCreatedAtAsc(userId, chatType);

        String lastQuestionText = history.stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .reduce((first, second) -> second) // last assistant message
                .map(AIMessage::getContent)
                .orElse("");

        // Save user message
        AIMessage userMsg = AIMessage.builder()
                .employee(employee)
                .role("user")
                .content(userMessage)
                .chatType(chatType)
                .userId(userId)
                .build();
        aiMessageRepository.save(userMsg);

        // Extract trait signals from user's answer via Claude
        String extractionSystemPrompt = String.format(
            "Ты анализируешь ответ на вопрос личностного теста для понимания стиля обучения.\n" +
            "Вопрос был: %s\n" +
            "Ответ пользователя: %s\n\n" +
            "Верни ТОЛЬКО валидный JSON без пояснений:\n" +
            "{\n" +
            "  \"O\": число от -1.0 до 1.0,\n" +
            "  \"C\": число от -1.0 до 1.0,\n" +
            "  \"E\": число от -1.0 до 1.0,\n" +
            "  \"A\": число от -1.0 до 1.0,\n" +
            "  \"N\": число от -1.0 до 1.0,\n" +
            "  \"reply\": \"Короткая реакция на ответ (1-2 предложения, дружелюбно, на русском)\"\n" +
            "}\n" +
            "Положительные значения = высокий уровень черты, отрицательные = низкий, 0 = нейтрально/неясно.",
            lastQuestionText, userMessage);

        Map<String, Double> signals = new HashMap<>(Map.of("O", 0.0, "C", 0.0, "E", 0.0, "A", 0.0, "N", 0.0));
        String replyText = "Понял, спасибо!";

        try {
            String extractionResult = callClaude(
                    "You are a psychometric assessment AI. Respond only with valid JSON.",
                    List.of(Map.of("role", "user", "content", extractionSystemPrompt))
            );
            String cleanJson = extractionResult.replaceAll("```json|```", "").trim();
            JsonNode signalNode = objectMapper.readTree(cleanJson);
            for (String trait : List.of("O", "C", "E", "A", "N")) {
                if (signalNode.has(trait)) {
                    signals.put(trait, signalNode.get(trait).asDouble(0.0));
                }
            }
            if (signalNode.has("reply")) {
                replyText = signalNode.get("reply").asText("Понял, спасибо!");
            }
        } catch (Exception e) {
            log.warn("Failed to parse trait signals from Claude response, using zeros: {}", e.getMessage());
        }

        // Find which question was last asked by looking at the last assistant message id
        // We need to find the question id from the last assistant message text
        // Match against bank questions to find which one was asked
        String lastAskedId = null;
        for (OceanQuestionBank.Question q : oceanQuestionBank.getAll()) {
            if (lastQuestionText.contains(q.text())) {
                lastAskedId = q.id();
                break;
            }
        }

        // Update confidence using signal * question's primary weight for that trait
        for (String trait : List.of("O", "C", "E", "A", "N")) {
            double signalVal = signals.getOrDefault(trait, 0.0);
            double weight = 0.5; // default if no question matched
            if (lastAskedId != null) {
                OceanQuestionBank.Question lastQ = oceanQuestionBank.getQuestion(lastAskedId).orElse(null);
                if (lastQ != null) {
                    weight = lastQ.traitWeights().getOrDefault(trait, 0.5);
                }
            }
            double updated = confidence.getOrDefault(trait, 0.0) + signalVal * weight;
            // Clamp to [-1.0, 1.0]
            confidence.put(trait, Math.max(-1.0, Math.min(1.0, updated)));
        }

        // Mark question as asked
        if (lastAskedId != null) {
            askedIds.add(lastAskedId);
        }
        questionCount++;

        // Step 5: Check if we should stop
        if (oceanQuestionBank.shouldStop(confidence, questionCount)) {
            // Compute OCEAN levels
            String oLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("O", 0.0));
            String cLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("C", 0.0));
            String eLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("E", 0.0));
            String aLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("A", 0.0));
            String nLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("N", 0.0));

            OCEANProfile updatedProfile = oceanProfileService.updateLevels(
                    employee.getId(), oLevel, cLevel, eLevel, aLevel, nLevel, questionCount);

            employee.setOnboardingCompleted(true);
            employeeRepository.save(employee);

            String learningProfile = updatedProfile.getLearningProfile();

            // Save state
            oceanProfile.setAssessmentState(serializeState(confidence, askedIds, questionCount));
            oceanProfileRepository.save(oceanProfile);

            String finalReply = replyText + "\n\nОтлично! Профиль готов. Твой стиль обучения: " + learningProfile;

            // Save assistant message
            AIMessage assistantMsg = AIMessage.builder()
                    .employee(employee)
                    .role("assistant")
                    .content(finalReply)
                    .chatType(chatType)
                    .userId(userId)
                    .build();
            aiMessageRepository.save(assistantMsg);

            return new HashMap<>(Map.of(
                    "reply", finalReply,
                    "completed", true,
                    "learningProfile", learningProfile,
                    "questionCount", questionCount
            ));
        }

        // Not done yet — select next question
        OceanQuestionBank.Question nextQuestion = oceanQuestionBank.selectNext(confidence, askedIds)
                .orElse(null);

        String assistantContent;
        if (nextQuestion != null) {
            assistantContent = replyText + "\n\n" + nextQuestion.text();
        } else {
            // No more questions — force completion
            String oLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("O", 0.0));
            String cLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("C", 0.0));
            String eLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("E", 0.0));
            String aLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("A", 0.0));
            String nLevel = oceanQuestionBank.toLevel(confidence.getOrDefault("N", 0.0));

            OCEANProfile updatedProfile = oceanProfileService.updateLevels(
                    employee.getId(), oLevel, cLevel, eLevel, aLevel, nLevel, questionCount);

            employee.setOnboardingCompleted(true);
            employeeRepository.save(employee);

            String learningProfile = updatedProfile.getLearningProfile();
            oceanProfile.setAssessmentState(serializeState(confidence, askedIds, questionCount));
            oceanProfileRepository.save(oceanProfile);

            assistantContent = replyText + "\n\nОтлично! Профиль готов. Твой стиль обучения: " + learningProfile;

            AIMessage assistantMsg = AIMessage.builder()
                    .employee(employee)
                    .role("assistant")
                    .content(assistantContent)
                    .chatType(chatType)
                    .userId(userId)
                    .build();
            aiMessageRepository.save(assistantMsg);

            return new HashMap<>(Map.of(
                    "reply", assistantContent,
                    "completed", true,
                    "learningProfile", learningProfile,
                    "questionCount", questionCount
            ));
        }

        // Save assistant message with next question
        AIMessage assistantMsg = AIMessage.builder()
                .employee(employee)
                .role("assistant")
                .content(assistantContent)
                .chatType(chatType)
                .userId(userId)
                .build();
        aiMessageRepository.save(assistantMsg);

        // Save assessment state
        oceanProfile.setAssessmentState(serializeState(confidence, askedIds, questionCount));
        oceanProfileRepository.save(oceanProfile);

        return new HashMap<>(Map.of(
                "reply", assistantContent,
                "completed", false,
                "learningProfile", "STRUCTURED_GUIDE",
                "questionCount", questionCount
        ));
    }

    private String serializeState(Map<String, Double> confidence, Set<String> askedIds, int questionCount) {
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("confidence", confidence);
            state.put("askedIds", new ArrayList<>(askedIds));
            state.put("questionCount", questionCount);
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.warn("Failed to serialize assessment state: {}", e.getMessage());
            return "{}";
        }
    }

    public List<AIMessage> getHistory(UUID employeeId, UUID materialId,
                                       UUID userId, String chatType) {
        if (materialId != null) {
            return aiMessageRepository.findByEmployeeIdAndMaterialIdOrderByCreatedAtAsc(employeeId, materialId);
        }
        return aiMessageRepository.findByUserIdAndChatTypeOrderByCreatedAtAsc(userId, chatType);
    }
}
