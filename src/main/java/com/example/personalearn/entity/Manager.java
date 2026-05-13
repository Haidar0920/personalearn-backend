package com.example.personalearn.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "managers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Manager {
    @Id
    private UUID id; // = Supabase auth.users.id (NOT generated — set from JWT sub)

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "company_name")
    private String companyName;

    private String position; // их должность: "HR Director", "Head of Sales", etc.

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
