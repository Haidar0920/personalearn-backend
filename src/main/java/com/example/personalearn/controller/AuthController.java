package com.example.personalearn.controller;

import com.example.personalearn.dto.request.RegisterRequest;
import com.example.personalearn.dto.response.EmployeeResponse;
import com.example.personalearn.dto.response.ManagerResponse;
import com.example.personalearn.entity.Employee;
import com.example.personalearn.entity.Manager;
import com.example.personalearn.repository.EmployeeRepository;
import com.example.personalearn.repository.ManagerRepository;
import com.example.personalearn.service.EmployeeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final ManagerRepository managerRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String supabaseServiceRoleKey;

    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody RegisterRequest req) {
        if (managerRepository.existsByEmail(req.email())) {
            return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));
        }

        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("email", req.email());
            bodyMap.put("password", req.password());
            bodyMap.put("email_confirm", true);
            bodyMap.put("app_metadata", Map.of("role", "client_admin"));

            String bodyJson = objectMapper.writeValueAsString(bodyMap);

            Request supabaseRequest = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/admin/users")
                    .addHeader("apikey", supabaseServiceRoleKey)
                    .addHeader("Authorization", "Bearer " + supabaseServiceRoleKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(bodyJson, MediaType.get("application/json")))
                    .build();

            String responseBody;
            try (Response response = httpClient.newCall(supabaseRequest).execute()) {
                responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("Supabase create user error: {} - {}", response.code(), responseBody);
                    return ResponseEntity.status(502).body(Map.of("error", "Failed to create user in Supabase: " + response.code()));
                }
            }

            JsonNode json = objectMapper.readTree(responseBody);
            String userId = json.path("id").asText(null);
            if (userId == null || userId.isBlank()) {
                log.error("Supabase response missing user id: {}", responseBody);
                return ResponseEntity.status(502).body(Map.of("error", "Supabase response missing user id"));
            }

            Manager manager = Manager.builder()
                    .id(UUID.fromString(userId))
                    .name(req.name())
                    .email(req.email())
                    .companyName(req.companyName())
                    .position(req.position())
                    .build();
            manager = managerRepository.save(manager);

            ManagerResponse managerResponse = new ManagerResponse(
                    manager.getId(),
                    manager.getName(),
                    manager.getEmail(),
                    manager.getCompanyName(),
                    manager.getPosition(),
                    manager.getCreatedAt()
            );
            return ResponseEntity.status(201).body(managerResponse);

        } catch (Exception e) {
            log.error("Register error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Registration failed"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        Map<String, Object> appMetadata = jwt.getClaimAsMap("app_metadata");
        String role = appMetadata != null ? (String) appMetadata.get("role") : null;
        if (role == null) {
            role = jwt.getClaimAsString("role");
        }

        if ("client_admin".equals(role)) {
            Manager manager = managerRepository.findById(userId).orElse(null);
            if (manager == null) {
                return ResponseEntity.notFound().build();
            }
            ManagerResponse profile = new ManagerResponse(
                    manager.getId(),
                    manager.getName(),
                    manager.getEmail(),
                    manager.getCompanyName(),
                    manager.getPosition(),
                    manager.getCreatedAt()
            );
            Map<String, Object> result = new HashMap<>();
            result.put("role", role);
            result.put("profile", profile);
            return ResponseEntity.ok(result);

        } else if ("client_user".equals(role)) {
            Employee employee = employeeRepository.findByUserId(userId).orElse(null);
            if (employee == null) {
                return ResponseEntity.notFound().build();
            }
            EmployeeResponse profile = employeeService.toResponse(employee);
            Map<String, Object> result = new HashMap<>();
            result.put("role", role);
            result.put("profile", profile);
            return ResponseEntity.ok(result);

        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("sub", jwt.getSubject());
            result.put("email", jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : "");
            result.put("role", role != null ? role : "user");
            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
