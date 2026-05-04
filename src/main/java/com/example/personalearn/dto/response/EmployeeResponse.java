package com.example.personalearn.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record EmployeeResponse(
        UUID id,
        String name,
        String email,
        String position,
        String department,
        String avatarInitials,
        String avatarColor,
        int trainingProgress,
        LocalDateTime createdAt
) {}
