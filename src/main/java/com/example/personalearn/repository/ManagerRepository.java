package com.example.personalearn.repository;

import com.example.personalearn.entity.Manager;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ManagerRepository extends JpaRepository<Manager, UUID> {
    Optional<Manager> findByEmail(String email);
    boolean existsByEmail(String email);
}
