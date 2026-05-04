package com.example.personalearn.dto.response;

import lombok.Builder;

@Builder
public record DashboardStatsResponse(
        long totalEmployees,
        long inTraining,
        long completedCourse,
        double averageAiScore
) {}
