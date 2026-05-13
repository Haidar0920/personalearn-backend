package com.example.personalearn.controller;

import com.example.personalearn.dto.request.AssignMaterialRequest;
import com.example.personalearn.dto.request.CompleteMaterialRequest;
import com.example.personalearn.dto.request.CreateEmployeeRequest;
import com.example.personalearn.dto.request.StartMaterialRequest;
import com.example.personalearn.dto.request.UpdateEmployeeRequest;
import com.example.personalearn.dto.response.DashboardStatsResponse;
import com.example.personalearn.dto.response.EmployeeResponse;
import com.example.personalearn.entity.Employee;
import com.example.personalearn.entity.EmployeeMaterial;
import com.example.personalearn.repository.EmployeeMaterialRepository;
import com.example.personalearn.repository.EmployeeRepository;
import com.example.personalearn.service.EmployeeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.web.bind.annotation.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeMaterialRepository employeeMaterialRepository;
    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String supabaseServiceRoleKey;

    @GetMapping
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<List<EmployeeResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search) {
        UUID managerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(employeeService.getEmployees(managerId, search));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EmployeeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return employeeRepository.findByUserId(userId)
                .map(emp -> ResponseEntity.ok(employeeService.toResponse(emp)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me/materials")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> myMaterials(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Employee employee = employeeRepository.findByUserId(userId).orElse(null);
        if (employee == null) {
            return ResponseEntity.ok(List.of());
        }
        List<EmployeeMaterial> assignments = employeeMaterialRepository.findByEmployeeId(employee.getId());
        List<Map<String, Object>> result = assignments.stream().map(em -> {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", em.getId());
            entry.put("employeeId", employee.getId());
            entry.put("materialId", em.getMaterial().getId());
            entry.put("title", em.getMaterial().getTitle());
            entry.put("description", em.getMaterial().getDescription());
            entry.put("type", em.getMaterial().getType());
            entry.put("fileUrl", em.getMaterial().getFileUrl());
            entry.put("status", em.getStatus());
            entry.put("progressPercent", em.getProgressPercent());
            entry.put("aiScore", em.getAiScore());
            entry.put("deadline", em.getDeadline());
            entry.put("startedAt", em.getStartedAt());
            entry.put("completedAt", em.getCompletedAt());
            return entry;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<EmployeeResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID managerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(employeeService.getEmployee(id, managerId));
    }

    @PostMapping
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<EmployeeResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateEmployeeRequest req) {
        UUID managerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(req, managerId));
    }

    @PostMapping("/link-user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EmployeeResponse> linkUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Employee employee = employeeRepository.findByEmail(email)
                .orElse(null);
        if (employee == null) {
            return ResponseEntity.notFound().build();
        }
        employee.setUserId(userId);
        employee = employeeRepository.save(employee);

        // Set client_user role in Supabase app_metadata
        try {
            String roleBody = objectMapper.writeValueAsString(
                    Map.of("app_metadata", Map.of("role", "client_user")));
            Request roleRequest = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/admin/users/" + userId)
                    .addHeader("apikey", supabaseServiceRoleKey)
                    .addHeader("Authorization", "Bearer " + supabaseServiceRoleKey)
                    .addHeader("Content-Type", "application/json")
                    .patch(okhttp3.RequestBody.create(roleBody, MediaType.get("application/json")))
                    .build();
            try (Response roleResponse = httpClient.newCall(roleRequest).execute()) {
                if (!roleResponse.isSuccessful()) {
                    String errBody = roleResponse.body() != null ? roleResponse.body().string() : "unknown";
                    log.warn("Failed to set client_user role in Supabase for {}: {} - {}", userId, roleResponse.code(), errBody);
                }
            }
        } catch (Exception e) {
            log.warn("Error setting client_user role in Supabase for {}: {}", userId, e.getMessage());
        }

        return ResponseEntity.ok(employeeService.toResponse(employee));
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Map<String, String>> invite(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID managerId = UUID.fromString(jwt.getSubject());
        Employee employee = employeeRepository.findById(id)
                .orElse(null);
        if (employee == null) {
            return ResponseEntity.notFound().build();
        }
        if (!employee.getCreatedBy().equals(managerId)) {
            return ResponseEntity.status(403).build();
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of("email", employee.getEmail()));
            Request request = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/invite")
                    .addHeader("apikey", supabaseServiceRoleKey)
                    .addHeader("Authorization", "Bearer " + supabaseServiceRoleKey)
                    .addHeader("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(body, MediaType.get("application/json")))
                    .build();
            String inviteResponseBody;
            try (Response response = httpClient.newCall(request).execute()) {
                inviteResponseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("Supabase invite error: {} - {}", response.code(), inviteResponseBody);
                    return ResponseEntity.status(502).body(Map.of("error", "Failed to send invite: " + response.code()));
                }
            }

            // Parse the new user's id from invite response and set client_user role
            try {
                JsonNode inviteJson = objectMapper.readTree(inviteResponseBody);
                String newUserId = inviteJson.path("id").asText(null);
                if (newUserId != null && !newUserId.isBlank()) {
                    String roleBody = objectMapper.writeValueAsString(
                            Map.of("app_metadata", Map.of("role", "client_user")));
                    Request roleRequest = new Request.Builder()
                            .url(supabaseUrl + "/auth/v1/admin/users/" + newUserId)
                            .addHeader("apikey", supabaseServiceRoleKey)
                            .addHeader("Authorization", "Bearer " + supabaseServiceRoleKey)
                            .addHeader("Content-Type", "application/json")
                            .patch(okhttp3.RequestBody.create(roleBody, MediaType.get("application/json")))
                            .build();
                    try (Response roleResponse = httpClient.newCall(roleRequest).execute()) {
                        if (!roleResponse.isSuccessful()) {
                            String errBody = roleResponse.body() != null ? roleResponse.body().string() : "unknown";
                            log.warn("Failed to set client_user role for invited user {}: {} - {}", newUserId, roleResponse.code(), errBody);
                        }
                    }
                } else {
                    log.warn("Invite response did not contain user id, skipping role assignment");
                }
            } catch (Exception e) {
                log.warn("Error setting client_user role after invite: {}", e.getMessage());
            }

            return ResponseEntity.ok(Map.of("message", "Invite sent to " + employee.getEmail()));
        } catch (Exception e) {
            log.error("Invite error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to send invite"));
        }
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateEmployeeRequest req) {
        UUID managerId = UUID.fromString(jwt.getSubject());
        Employee employee = employeeRepository.findById(id)
                .orElse(null);
        if (employee == null) {
            return ResponseEntity.notFound().build();
        }
        if (!employee.getCreatedBy().equals(managerId)) {
            return ResponseEntity.status(403).build();
        }
        if (req.name() != null) employee.setName(req.name());
        if (req.position() != null) employee.setPosition(req.position());
        if (req.department() != null) employee.setDepartment(req.department());
        employee = employeeRepository.save(employee);
        return ResponseEntity.ok(employeeService.toResponse(employee));
    }

    @PostMapping("/{id}/assign-material")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Void> assignMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody AssignMaterialRequest req) {
        employeeService.assignMaterial(id, req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/start-material")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> startMaterial(
            @PathVariable UUID id,
            @RequestBody StartMaterialRequest req) {
        EmployeeMaterial em = employeeMaterialRepository
                .findByEmployeeIdAndMaterialId(id, req.materialId())
                .orElse(null);
        if (em == null) {
            return ResponseEntity.notFound().build();
        }
        em.setStatus("in_progress");
        em.setStartedAt(LocalDateTime.now());
        employeeMaterialRepository.save(em);
        return ResponseEntity.ok(Map.of("status", "in_progress"));
    }

    @PostMapping("/{id}/complete-material")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> completeMaterial(
            @PathVariable UUID id,
            @RequestBody CompleteMaterialRequest req) {
        EmployeeMaterial em = employeeMaterialRepository
                .findByEmployeeIdAndMaterialId(id, req.materialId())
                .orElse(null);
        if (em == null) {
            return ResponseEntity.notFound().build();
        }
        em.setStatus("completed");
        em.setProgressPercent(100);
        em.setAiScore(req.aiScore());
        em.setCompletedAt(LocalDateTime.now());
        employeeMaterialRepository.save(em);
        return ResponseEntity.ok(Map.of("status", "completed"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard-stats")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<DashboardStatsResponse> stats(@AuthenticationPrincipal Jwt jwt) {
        UUID managerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(employeeService.getDashboardStats(managerId));
    }
}
