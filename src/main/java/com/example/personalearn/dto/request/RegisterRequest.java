package com.example.personalearn.dto.request;

public record RegisterRequest(
    String name,
    String email,
    String password,
    String companyName,
    String position
) {}
