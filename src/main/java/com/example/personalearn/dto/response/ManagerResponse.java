package com.example.personalearn.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ManagerResponse(
    UUID id,
    String name,
    String email,
    String companyName,
    String position,
    LocalDateTime createdAt
) {}
