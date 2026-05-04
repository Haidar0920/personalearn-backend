package com.example.personalearn.repository;

import com.example.personalearn.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    List<Material> findByCreatedByAndTypeOrderByCreatedAtDesc(UUID createdBy, String type);

    List<Material> findByCreatedByAndIsPublishedTrueOrderByCreatedAtDesc(UUID createdBy);
}
