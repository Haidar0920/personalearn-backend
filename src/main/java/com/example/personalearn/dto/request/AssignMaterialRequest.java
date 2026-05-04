package com.example.personalearn.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssignMaterialRequest(
        @NotNull UUID materialId,
        LocalDateTime deadline,
        String goal
) {}
