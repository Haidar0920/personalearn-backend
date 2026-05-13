package com.example.personalearn.dto.request;

public record UpdateEmployeeRequest(
        String name,
        String position,
        String department
) {}
