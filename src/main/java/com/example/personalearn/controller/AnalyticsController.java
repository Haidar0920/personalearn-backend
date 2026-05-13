package com.example.personalearn.controller;

import com.example.personalearn.entity.Employee;
import com.example.personalearn.entity.EmployeeMaterial;
import com.example.personalearn.entity.OCEANProfile;
import com.example.personalearn.repository.AIMessageRepository;
import com.example.personalearn.repository.EmployeeMaterialRepository;
import com.example.personalearn.repository.EmployeeRepository;
import com.example.personalearn.repository.OCEANProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AnalyticsController {

    private final EmployeeRepository employeeRepository;
    private final OCEANProfileRepository oceanProfileRepository;
    private final EmployeeMaterialRepository employeeMaterialRepository;
    private final AIMessageRepository aiMessageRepository;

    /** GET /analytics/overview - manager dashboard summary */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(@AuthenticationPrincipal Jwt jwt) {
        UUID managerId = UUID.fromString(jwt.getSubject());

        List<Employee> employees = employeeRepository.findByCreatedBy(managerId);
        long totalEmployees = employees.size();
        long onboardingCompleted = employees.stream()
                .filter(e -> Boolean.TRUE.equals(e.getOnboardingCompleted()))
                .count();
        long inTraining = employeeMaterialRepository.countInTrainingByManager(managerId);
        long completed = employeeMaterialRepository.countCompletedByManager(managerId);
        Double avgAiScore = employeeMaterialRepository.avgAiScoreByManager(managerId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEmployees", totalEmployees);
        result.put("onboardingCompleted", onboardingCompleted);
        result.put("inTraining", inTraining);
        result.put("completed", completed);
        result.put("avgAiScore", avgAiScore != null ? Math.round(avgAiScore * 10.0) / 10.0 : 0.0);

        return ResponseEntity.ok(result);
    }

    /** GET /analytics/employees - detailed list with OCEAN profiles and scores */
    @GetMapping("/employees")
    public ResponseEntity<List<Map<String, Object>>> employees(@AuthenticationPrincipal Jwt jwt) {
        UUID managerId = UUID.fromString(jwt.getSubject());

        List<Employee> employees = employeeRepository.findByCreatedBy(managerId);

        List<Map<String, Object>> result = employees.stream().map(emp -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", emp.getId());
            entry.put("name", emp.getName());
            entry.put("email", emp.getEmail());
            entry.put("position", emp.getPosition());
            entry.put("department", emp.getDepartment());
            entry.put("onboardingCompleted", emp.getOnboardingCompleted());

            OCEANProfile ocean = oceanProfileRepository.findByEmployeeId(emp.getId()).orElse(null);
            if (ocean != null) {
                entry.put("learningProfile", ocean.getLearningProfile());
                entry.put("oLevel", ocean.getOLevel());
                entry.put("cLevel", ocean.getCLevel());
                entry.put("eLevel", ocean.getELevel());
                entry.put("aLevel", ocean.getALevel());
                entry.put("nLevel", ocean.getNLevel());
            } else {
                entry.put("learningProfile", null);
            }

            Double avgProgress = employeeMaterialRepository.avgProgressByEmployee(emp.getId());
            entry.put("avgProgress", avgProgress != null ? avgProgress.intValue() : 0);

            List<EmployeeMaterial> materials = employeeMaterialRepository.findByEmployeeId(emp.getId());
            OptionalDouble avgScore = materials.stream()
                    .filter(m -> m.getAiScore() != null)
                    .mapToDouble(EmployeeMaterial::getAiScore)
                    .average();
            entry.put("avgAiScore", avgScore.isPresent() ? Math.round(avgScore.getAsDouble() * 10.0) / 10.0 : null);
            entry.put("totalMaterials", materials.size());
            entry.put("completedMaterials", materials.stream().filter(m -> "completed".equals(m.getStatus())).count());

            return entry;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /** GET /analytics/employees/{id} - single employee full analytics */
    @GetMapping("/employees/{id}")
    public ResponseEntity<Map<String, Object>> employeeDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID managerId = UUID.fromString(jwt.getSubject());

        Employee emp = employeeRepository.findById(id).orElse(null);
        if (emp == null) return ResponseEntity.notFound().build();
        if (!emp.getCreatedBy().equals(managerId)) return ResponseEntity.status(403).build();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", emp.getId());
        result.put("userId", emp.getUserId());
        result.put("name", emp.getName());
        result.put("email", emp.getEmail());
        result.put("position", emp.getPosition());
        result.put("department", emp.getDepartment());
        result.put("onboardingCompleted", emp.getOnboardingCompleted());
        result.put("createdAt", emp.getCreatedAt());

        OCEANProfile ocean = oceanProfileRepository.findByEmployeeId(emp.getId()).orElse(null);
        if (ocean != null) {
            Map<String, Object> oceanMap = new LinkedHashMap<>();
            oceanMap.put("learningProfile", ocean.getLearningProfile());
            oceanMap.put("oLevel", ocean.getOLevel());
            oceanMap.put("cLevel", ocean.getCLevel());
            oceanMap.put("eLevel", ocean.getELevel());
            oceanMap.put("aLevel", ocean.getALevel());
            oceanMap.put("nLevel", ocean.getNLevel());
            oceanMap.put("assessmentTurns", ocean.getAssessmentTurns());
            oceanMap.put("lastUpdated", ocean.getLastUpdated());
            result.put("oceanProfile", oceanMap);
        } else {
            result.put("oceanProfile", null);
        }

        List<EmployeeMaterial> materials = employeeMaterialRepository.findByEmployeeId(emp.getId());
        List<Map<String, Object>> materialList = materials.stream().map(m -> {
            Map<String, Object> mat = new LinkedHashMap<>();
            mat.put("id", m.getId());
            mat.put("materialId", m.getMaterial().getId());
            mat.put("title", m.getMaterial().getTitle());
            mat.put("type", m.getMaterial().getType());
            mat.put("status", m.getStatus());
            mat.put("progressPercent", m.getProgressPercent());
            mat.put("aiScore", m.getAiScore());
            mat.put("startedAt", m.getStartedAt());
            mat.put("completedAt", m.getCompletedAt());
            mat.put("deadline", m.getDeadline());
            return mat;
        }).collect(Collectors.toList());
        result.put("materials", materialList);

        OptionalDouble avgScore = materials.stream()
                .filter(m -> m.getAiScore() != null)
                .mapToDouble(EmployeeMaterial::getAiScore)
                .average();
        result.put("avgAiScore", avgScore.isPresent() ? Math.round(avgScore.getAsDouble() * 10.0) / 10.0 : null);

        return ResponseEntity.ok(result);
    }
}
