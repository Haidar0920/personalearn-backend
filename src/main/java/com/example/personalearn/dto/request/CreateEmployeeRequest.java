package com.example.personalearn.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreateEmployeeRequest(
        @NotBlank String name,
        @Email @NotBlank String email,
        String position,
        String department,
        List<UUID> materialIds,
        LocalDateTime deadline,
        String goal
) {}
