package com.example.personalearn.repository;

import com.example.personalearn.entity.EmployeeMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeMaterialRepository extends JpaRepository<EmployeeMaterial, UUID> {

    List<EmployeeMaterial> findByEmployeeId(UUID employeeId);

    Optional<EmployeeMaterial> findByEmployeeIdAndMaterialId(UUID employeeId, UUID materialId);

    @Query("SELECT COUNT(em) FROM EmployeeMaterial em WHERE em.employee.createdBy = :managerId AND em.status = 'in_progress'")
    long countInTrainingByManager(UUID managerId);

    @Query("SELECT COUNT(em) FROM EmployeeMaterial em WHERE em.employee.createdBy = :managerId AND em.status = 'completed'")
    long countCompletedByManager(UUID managerId);

    @Query("SELECT AVG(em.aiScore) FROM EmployeeMaterial em WHERE em.employee.createdBy = :managerId AND em.aiScore IS NOT NULL")
    Double avgAiScoreByManager(UUID managerId);

    @Query("SELECT AVG(em.progressPercent) FROM EmployeeMaterial em WHERE em.employee.id = :employeeId")
    Double avgProgressByEmployee(UUID employeeId);
}
