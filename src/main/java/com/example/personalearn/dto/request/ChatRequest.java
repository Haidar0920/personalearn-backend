package com.example.personalearn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatRequest(
        @NotNull UUID employeeId,
        UUID materialId,       // optional — null = general chat
        @NotBlank String message,
        String chatType        // "employee_chat" | "admin_chat", default = "employee_chat"
) {}
