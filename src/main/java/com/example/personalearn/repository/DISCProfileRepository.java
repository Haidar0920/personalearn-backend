package com.example.personalearn.repository;

import com.example.personalearn.entity.DISCProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DISCProfileRepository extends JpaRepository<DISCProfile, UUID> {

    Optional<DISCProfile> findByEmployeeId(UUID employeeId);
}
