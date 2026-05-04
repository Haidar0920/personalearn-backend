package com.example.personalearn.service;

import com.example.personalearn.entity.Employee;
import com.example.personalearn.entity.OCEANProfile;
import com.example.personalearn.repository.OCEANProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages OCEAN personality profiling and LLM rules generation.
 *
 * Spec: OCEAN scores normalize into low/medium/high levels,
 * then resolve into one of four learning profiles,
 * generating a JSON rules object for the LLM.
 *
 * Priority order: C → N → O → A → E
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OCEANProfileService {

    private final OCEANProfileRepository oceanProfileRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get or create an OCEAN profile for an employee.
     * New profiles default to STRUCTURED_GUIDE.
     */
    public OCEANProfile getOrCreate(Employee employee) {
        return oceanProfileRepository.findByEmployeeId(employee.getId())
                .orElseGet(() -> {
                    OCEANProfile profile = OCEANProfile.builder()
                            .employee(employee)
                            .build();
                    profile.setLlmRules(buildLlmRules(profile));
                    return oceanProfileRepository.save(profile);
                });
    }

    /**
     * Update OCEAN levels and recompute profile + LLM rules.
     * Called after AI analyzes enough conversation turns.
     */
    @Transactional
    public OCEANProfile updateLevels(UUID employeeId,
                                     String oLevel, String cLevel,
                                     String eLevel, String aLevel,
                                     String nLevel, int turns) {
        OCEANProfile profile = oceanProfileRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("OCEAN profile not found"));

        profile.setOLevel(oLevel);
        profile.setCLevel(cLevel);
        profile.setELevel(eLevel);
        profile.setALevel(aLevel);
        profile.setNLevel(nLevel);
        profile.setAssessmentTurns(turns);
        profile.setLearningProfile(assignProfile(oLevel, cLevel, eLevel, aLevel, nLevel));
        profile.setLlmRules(buildLlmRules(profile));

        return oceanProfileRepository.save(profile);
    }

    /**
     * Assign learning profile using priority rules:
     * C → N → O → A → E → default: STRUCTURED_GUIDE
     */
    public String assignProfile(String o, String c, String e, String a, String n) {
        // Rule 1: High Conscientiousness (+ High Openness) → CONCEPT_EXPLORER
        if (isHigh(c) && isHigh(o)) return "CONCEPT_EXPLORER";

        // Rule 2: High Conscientiousness → STRUCTURED_GUIDE
        if (isHigh(c)) return "STRUCTURED_GUIDE";

        // Rule 3: High Neuroticism → SUPPORTIVE_SPRINT
        if (isHigh(n)) return "SUPPORTIVE_SPRINT";

        // Rule 4: High Openness → CONCEPT_EXPLORER
        if (isHigh(o)) return "CONCEPT_EXPLORER";

        // Rule 5: High Extraversion + Low Neuroticism → ACTION_RUNNER
        if (isHigh(e) && isLow(n)) return "ACTION_RUNNER";

        // Default
        return "STRUCTURED_GUIDE";
    }

    /**
     * Build the JSON rules object that gets injected into the LLM system prompt.
     */
    public String buildLlmRules(OCEANProfile profile) {
        Map<String, String> rules = new LinkedHashMap<>();

        switch (profile.getLearningProfile()) {
            case "CONCEPT_EXPLORER" -> {
                rules.put("content_order", "concept-first");
                rules.put("content_length", "detailed");
                rules.put("structure_level", "medium");
                rules.put("example_density", "many");
                rules.put("interaction_style", "calm");
                rules.put("feedback_tone", "direct");
                rules.put("check_frequency", "medium");
                rules.put("difficulty_progression", "moderate");
                rules.put("autonomy_level", "medium");
            }
            case "SUPPORTIVE_SPRINT" -> {
                rules.put("content_order", "instruction-first");
                rules.put("content_length", "short");
                rules.put("structure_level", "very-high");
                rules.put("example_density", "few");
                rules.put("interaction_style", "conversational");
                rules.put("feedback_tone", "supportive");
                rules.put("check_frequency", "very-high");
                rules.put("difficulty_progression", "gradual");
                rules.put("autonomy_level", "low");
            }
            case "ACTION_RUNNER" -> {
                rules.put("content_order", "instruction-first");
                rules.put("content_length", "short");
                rules.put("structure_level", "low");
                rules.put("example_density", "scenario-based");
                rules.put("interaction_style", "conversational");
                rules.put("feedback_tone", "direct");
                rules.put("check_frequency", "low");
                rules.put("difficulty_progression", "fast");
                rules.put("autonomy_level", "high");
            }
            default -> { // STRUCTURED_GUIDE
                rules.put("content_order", "instruction-first");
                rules.put("content_length", "medium");
                rules.put("structure_level", "high");
                rules.put("example_density", "moderate");
                rules.put("interaction_style", "calm");
                rules.put("feedback_tone", "supportive");
                rules.put("check_frequency", "medium");
                rules.put("difficulty_progression", "moderate");
                rules.put("autonomy_level", "low");
            }
        }

        try {
            return objectMapper.writeValueAsString(rules);
        } catch (Exception ex) {
            log.error("Error serializing LLM rules", ex);
            return "{}";
        }
    }

    private boolean isHigh(String level) {
        return "high".equalsIgnoreCase(level);
    }

    private boolean isLow(String level) {
        return "low".equalsIgnoreCase(level);
    }
}
