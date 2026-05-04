package com.example.personalearn.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        LocalDateTime createdAt
) {}
