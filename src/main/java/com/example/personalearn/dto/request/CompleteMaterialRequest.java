package com.example.personalearn.dto.request;

import java.util.UUID;

public record CompleteMaterialRequest(UUID materialId, Double aiScore) {}
