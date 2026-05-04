package com.example.personalearn.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "disc_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DISCProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    /** D: Dominance — напористость, результат */
    @Column(name = "d_score")
    private Integer dScore;

    /** I: Influence — общительность, убеждение */
    @Column(name = "i_score")
    private Integer iScore;

    /** S: Steadiness — стабильность, командная работа */
    @Column(name = "s_score")
    private Integer sScore;

    /** C: Conscientiousness — аналитичность, точность */
    @Column(name = "c_score")
    private Integer cScore;

    /** Dominant type: D | I | S | C | DI | DC | IS | SC | ... */
    @Column(name = "dominant_type", length = 10)
    private String dominantType;

    /** Характеристики типа */
    @Column(name = "characteristics", columnDefinition = "TEXT")
    private String characteristics;

    /** AI-резюме по поведению сотрудника */
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    /** AI-рекомендации для руководителя */
    @Column(name = "ai_recommendations", columnDefinition = "TEXT")
    private String aiRecommendations;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
