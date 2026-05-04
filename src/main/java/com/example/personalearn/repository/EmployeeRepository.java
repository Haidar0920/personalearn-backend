package com.example.personalearn.repository;

import com.example.personalearn.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    List<Employee> findByCreatedBy(UUID createdBy);

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.createdBy = :managerId")
    long countByManager(UUID managerId);

    @Query("""
        SELECT e FROM Employee e
        WHERE e.createdBy = :managerId
        AND (LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(e.position) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    List<Employee> searchByManager(UUID managerId, String search);
}
