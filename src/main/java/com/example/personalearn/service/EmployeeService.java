package com.example.personalearn.service;

import com.example.personalearn.dto.request.AssignMaterialRequest;
import com.example.personalearn.dto.request.CreateEmployeeRequest;
import com.example.personalearn.dto.response.DashboardStatsResponse;
import com.example.personalearn.dto.response.EmployeeResponse;
import com.example.personalearn.entity.Employee;
import com.example.personalearn.entity.EmployeeMaterial;
import com.example.personalearn.entity.Material;
import com.example.personalearn.repository.EmployeeMaterialRepository;
import com.example.personalearn.repository.EmployeeRepository;
import com.example.personalearn.repository.MaterialRepository;
import com.example.personalearn.repository.OCEANProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final MaterialRepository materialRepository;
    private final EmployeeMaterialRepository employeeMaterialRepository;
    private final OCEANProfileRepository oceanProfileRepository;

    public List<EmployeeResponse> getEmployees(UUID managerId, String search) {
        List<Employee> employees = (search != null && !search.isBlank())
                ? employeeRepository.searchByManager(managerId, search)
                : employeeRepository.findByCreatedBy(managerId);

        return employees.stream().map(this::toResponse).toList();
    }

    public EmployeeResponse getEmployee(UUID id, UUID managerId) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (!e.getCreatedBy().equals(managerId)) {
            throw new RuntimeException("Access denied");
        }
        return toResponse(e);
    }

    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest req, UUID managerId) {
        if (employeeRepository.existsByEmail(req.email())) {
            throw new RuntimeException("Employee with this email already exists");
        }

        Employee employee = Employee.builder()
                .name(req.name())
                .email(req.email())
                .position(req.position())
                .department(req.department())
                .avatarInitials(generateInitials(req.name()))
                .avatarColor(randomColor())
                .createdBy(managerId)
                .build();

        final Employee savedEmployee = employeeRepository.save(employee);

        // Assign materials if provided
        if (req.materialIds() != null && !req.materialIds().isEmpty()) {
            for (UUID materialId : req.materialIds()) {
                materialRepository.findById(materialId).ifPresent(material -> {
                    EmployeeMaterial em = EmployeeMaterial.builder()
                            .employee(savedEmployee)
                            .material(material)
                            .deadline(req.deadline())
                            .goal(req.goal())
                            .build();
                    employeeMaterialRepository.save(em);
                });
            }
        }

        log.info("Employee created: {} by manager {}", savedEmployee.getId(), managerId);
        return toResponse(savedEmployee);
    }

    @Transactional
    public void assignMaterial(UUID employeeId, AssignMaterialRequest req) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Material material = materialRepository.findById(req.materialId())
                .orElseThrow(() -> new RuntimeException("Material not found"));

        boolean exists = employeeMaterialRepository
                .findByEmployeeIdAndMaterialId(employeeId, req.materialId()).isPresent();
        if (exists) {
            throw new RuntimeException("Material already assigned to this employee");
        }

        EmployeeMaterial em = EmployeeMaterial.builder()
                .employee(employee)
                .material(material)
                .deadline(req.deadline())
                .goal(req.goal())
                .build();
        employeeMaterialRepository.save(em);
    }

    public DashboardStatsResponse getDashboardStats(UUID managerId) {
        long total = employeeRepository.countByManager(managerId);
        long inTraining = employeeMaterialRepository.countInTrainingByManager(managerId);
        long completed = employeeMaterialRepository.countCompletedByManager(managerId);
        Double avgScore = employeeMaterialRepository.avgAiScoreByManager(managerId);

        return DashboardStatsResponse.builder()
                .totalEmployees(total)
                .inTraining(inTraining)
                .completedCourse(completed)
                .averageAiScore(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0)
                .build();
    }

    @Transactional
    public void deleteEmployee(UUID id) {
        employeeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse toResponse(Employee e) {
        Double progress = employeeMaterialRepository.avgProgressByEmployee(e.getId());
        String learningProfile = oceanProfileRepository
                .findByEmployeeId(e.getId())
                .map(p -> p.getLearningProfile())
                .orElse(null);
        return EmployeeResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .name(e.getName())
                .email(e.getEmail())
                .position(e.getPosition())
                .department(e.getDepartment())
                .avatarInitials(e.getAvatarInitials())
                .avatarColor(e.getAvatarColor())
                .trainingProgress(progress != null ? progress.intValue() : 0)
                .onboardingCompleted(e.getOnboardingCompleted())
                .oceanLearningProfile(learningProfile)
                .createdAt(e.getCreatedAt())
                .build();
    }

    private String generateInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase();
    }

    private String randomColor() {
        String[] colors = {"blue", "emerald", "amber", "violet", "rose", "cyan", "orange"};
        return colors[(int) (Math.random() * colors.length)];
    }
}
