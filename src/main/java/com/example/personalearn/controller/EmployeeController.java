package com.example.personalearn.controller;

import com.example.personalearn.dto.request.AssignMaterialRequest;
import com.example.personalearn.dto.request.CreateEmployeeRequest;
import com.example.personalearn.dto.response.DashboardStatsResponse;
import com.example.personalearn.dto.response.EmployeeResponse;
import com.example.personalearn.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<List<EmployeeResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search) {
        UUID managerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(employeeService.getEmployees(managerId, search));
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

    @PostMapping("/{id}/assign-material")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Void> assignMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody AssignMaterialRequest req) {
        employeeService.assignMaterial(id, req);
        return ResponseEntity.ok().build();
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
