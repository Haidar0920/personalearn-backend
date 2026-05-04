package com.example.personalearn.repository;

import com.example.personalearn.entity.OCEANProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OCEANProfileRepository extends JpaRepository<OCEANProfile, UUID> {

    Optional<OCEANProfile> findByEmployeeId(UUID employeeId);
}
