package com.example.personalearn.service;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class OceanQuestionBank {

    public record Question(String id, String text, Map<String, Double> traitWeights) {}

    // 25 questions, grouped by primary trait
    // traitWeights: which traits this question reveals, with strength 0.0-1.0
    // Positive weight = question probes HIGH level of that trait
    private static final List<Question> QUESTIONS = List.of(
        // C — Conscientiousness (structure, planning, order)
        new Question("C1", "Когда берёшься за незнакомую задачу — что делаешь первым?",
            Map.of("C", 1.0)),
        new Question("C2", "Как относишься к работе без чёткого плана или инструкций?",
            Map.of("C", 0.9, "O", 0.3)),
        new Question("C3", "Дедлайн сдвинули на день раньше — как реагируешь?",
            Map.of("C", 0.8, "N", 0.4)),
        new Question("C4", "Тебе важно понять все детали до начала или разберёшься по ходу?",
            Map.of("C", 0.9, "O", -0.3)),
        new Question("C5", "Как обычно выглядит твой рабочий день — по расписанию или по ситуации?",
            Map.of("C", 1.0)),

        // N — Neuroticism (stress, emotional reaction)
        new Question("N1", "Что происходит внутри, когда допускаешь серьёзную ошибку?",
            Map.of("N", 1.0)),
        new Question("N2", "Как реагируешь на критику от руководителя?",
            Map.of("N", 0.8, "A", 0.3)),
        new Question("N3", "Когда задач слишком много и сроки горят — опиши своё состояние",
            Map.of("N", 1.0)),
        new Question("N4", "Долго ли переживаешь из-за рабочих неудач?",
            Map.of("N", 0.9)),
        new Question("N5", "Ты склонен заранее беспокоиться о возможных проблемах?",
            Map.of("N", 0.8, "C", 0.2)),

        // O — Openness (curiosity, new experiences, creativity)
        new Question("O1", "Что привлекает больше — углубиться в одну тему или узнать несколько новых?",
            Map.of("O", 1.0)),
        new Question("O2", "Ты любишь экспериментировать или предпочитаешь проверенные методы?",
            Map.of("O", 0.9)),
        new Question("O3", "Тебе важно понять 'почему это работает' или достаточно знать 'как делать'?",
            Map.of("O", 1.0, "C", 0.2)),
        new Question("O4", "Как относишься к задачам где нет правильного ответа?",
            Map.of("O", 0.8, "N", -0.3)),
        new Question("O5", "Расскажи о последнем разе когда попробовал что-то совсем незнакомое",
            Map.of("O", 0.7)),

        // E — Extraversion (energy, social, initiative)
        new Question("E1", "Учиться предпочитаешь самостоятельно или в группе с коллегами?",
            Map.of("E", 1.0)),
        new Question("E2", "После насыщенного рабочего дня тебе нужно побыть одному или пообщаться?",
            Map.of("E", 0.9)),
        new Question("E3", "Легко ли берёшь инициативу в обсуждениях и встречах?",
            Map.of("E", 0.8, "N", -0.3)),
        new Question("E4", "Ты обычно думаешь вслух или предпочитаешь сначала обдумать про себя?",
            Map.of("E", 0.7)),
        new Question("E5", "Как быстро принимаешь решения — сразу или долго взвешиваешь?",
            Map.of("E", 0.6, "C", -0.3)),

        // A — Agreeableness (cooperation, trust, empathy)
        new Question("A1", "Легко ли соглашаешься с мнением коллеги, даже если не уверен?",
            Map.of("A", 0.9)),
        new Question("A2", "Как реагируешь, когда видишь что коллеге трудно с задачей?",
            Map.of("A", 1.0)),
        new Question("A3", "Компромисс или отстоять свою позицию — что ближе?",
            Map.of("A", 0.8)),
        new Question("A4", "Ты доверяешь людям по умолчанию или сначала проверяешь?",
            Map.of("A", 0.7)),
        new Question("A5", "Тебе важно чтобы команда была согласна с решением?",
            Map.of("A", 0.8, "E", 0.2))
    );

    // Trait priority for PersonaLearn profiles (C most important, A least)
    public static final List<String> TRAIT_PRIORITY = List.of("C", "N", "O", "E", "A");

    public List<Question> getAll() { return QUESTIONS; }

    public Optional<Question> getQuestion(String id) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).findFirst();
    }

    /** Get next question: targets least-confident trait, excludes already asked */
    public Optional<Question> selectNext(Map<String, Double> confidence, Set<String> askedIds) {
        // Find trait with lowest absolute confidence, in priority order
        String targetTrait = TRAIT_PRIORITY.stream()
            .min(Comparator.comparingDouble(t -> Math.abs(confidence.getOrDefault(t, 0.0))))
            .orElse("C");

        // Find questions for this trait not yet asked
        List<Question> candidates = QUESTIONS.stream()
            .filter(q -> !askedIds.contains(q.id()))
            .filter(q -> q.traitWeights().getOrDefault(targetTrait, 0.0) >= 0.7)
            .toList();

        if (candidates.isEmpty()) {
            // Fall back to any unasked question
            candidates = QUESTIONS.stream()
                .filter(q -> !askedIds.contains(q.id()))
                .toList();
        }

        if (candidates.isEmpty()) return Optional.empty();

        // Pick deterministically but variably (avoid always picking first)
        int idx = (int)(System.currentTimeMillis() % candidates.size());
        return Optional.of(candidates.get(idx));
    }

    /** Check if we have enough confidence to stop */
    public boolean shouldStop(Map<String, Double> confidence, int questionCount) {
        if (questionCount < 5) return false;
        if (questionCount >= 10) return true;

        // Stop if all priority traits have confidence >= 0.65
        double minConfidence = TRAIT_PRIORITY.stream()
            .mapToDouble(t -> Math.abs(confidence.getOrDefault(t, 0.0)))
            .min().orElse(0.0);

        return minConfidence >= 0.65;
    }

    /** Convert confidence scores to OCEAN levels for OCEANProfileService */
    public String toLevel(double confidence) {
        if (confidence >= 0.4) return "high";
        if (confidence <= -0.4) return "low";
        return "medium";
    }
}
