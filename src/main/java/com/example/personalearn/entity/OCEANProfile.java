package com.example.personalearn.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OCEAN (Big Five) personality profile — used to adapt HOW content is delivered.
 *
 * Four learning profiles derived from OCEAN:
 * - STRUCTURED_GUIDE: High C → step-by-step, checklists, medium length
 * - CONCEPT_EXPLORER:  High O + C → detailed explanations, models before procedures
 * - SUPPORTIVE_SPRINT: High N → short blocks, high structure, frequent checks, supportive tone
 * - ACTION_RUNNER:     High E + low N → scenario-based, fast-paced, doing over theory
 *
 * Priority order: C → N → O → A → E (defaulting to STRUCTURED_GUIDE if unclear)
 */
@Entity
@Table(name = "ocean_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OCEANProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    /** Openness: low | medium | high */
    @Column(name = "o_level", length = 10)
    @Builder.Default
    private String oLevel = "medium";

    /** Conscientiousness: low | medium | high */
    @Column(name = "c_level", length = 10)
    @Builder.Default
    private String cLevel = "medium";

    /** Extraversion: low | medium | high */
    @Column(name = "e_level", length = 10)
    @Builder.Default
    private String eLevel = "medium";

    /** Agreeableness: low | medium | high */
    @Column(name = "a_level", length = 10)
    @Builder.Default
    private String aLevel = "medium";

    /** Neuroticism: low | medium | high */
    @Column(name = "n_level", length = 10)
    @Builder.Default
    private String nLevel = "low";

    /**
     * Assigned learning profile:
     * STRUCTURED_GUIDE | CONCEPT_EXPLORER | SUPPORTIVE_SPRINT | ACTION_RUNNER
     */
    @Column(name = "learning_profile", length = 30)
    @Builder.Default
    private String learningProfile = "STRUCTURED_GUIDE";

    /**
     * JSON rules object passed to the LLM for content adaptation.
     * Example:
     * {
     *   "content_order": "concept-first",
     *   "content_length": "detailed",
     *   "structure_level": "high",
     *   "example_density": "many",
     *   "interaction_style": "calm",
     *   "feedback_tone": "supportive",
     *   "check_frequency": "medium",
     *   "difficulty_progression": "gradual",
     *   "autonomy_level": "low"
     * }
     */
    @Column(name = "llm_rules", columnDefinition = "TEXT")
    private String llmRules;

    /** Number of AI dialog turns used to build this profile */
    @Column(name = "assessment_turns")
    @Builder.Default
    private Integer assessmentTurns = 0;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
