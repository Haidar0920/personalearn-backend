package com.example.personalearn.controller;

import com.example.personalearn.entity.Material;
import com.example.personalearn.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialRepository materialRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Material>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String type) {
        UUID createdBy = UUID.fromString(jwt.getSubject());
        List<Material> materials = (type != null && !type.isBlank())
                ? materialRepository.findByCreatedByAndTypeOrderByCreatedAtDesc(createdBy, type)
                : materialRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
        return ResponseEntity.ok(materials);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Material> get(@PathVariable UUID id) {
        // Any authenticated user can read a material (employees need to view assigned materials)
        Material material = materialRepository.findById(id).orElse(null);
        if (material == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(material);
    }

    /**
     * Upload a material file + metadata.
     * File is stored in Supabase Storage via frontend (presigned URL flow).
     * This endpoint stores metadata after upload.
     */
    @PostMapping
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Material> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String type,
            @RequestParam(required = false) String goal,
            @RequestParam(required = false) String audience,
            @RequestParam String fileUrl,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) Long fileSize,
            @RequestParam(required = false) String content,
            @RequestParam(defaultValue = "false") boolean publish) {

        UUID createdBy = UUID.fromString(jwt.getSubject());
        Material material = Material.builder()
                .title(title)
                .description(description)
                .type(type)
                .goal(goal)
                .audience(audience)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .content(content)
                .isPublished(publish)
                .createdBy(createdBy)
                .build();

        material = materialRepository.save(material);
        log.info("Material created: {} by {}", material.getId(), createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(material);
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Material> publish(@PathVariable UUID id) {
        Material material = materialRepository.findById(id).orElse(null);
        if (material == null) return ResponseEntity.notFound().build();
        material.setIsPublished(true);
        return ResponseEntity.ok(materialRepository.save(material));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID createdBy = UUID.fromString(jwt.getSubject());
        Material material = materialRepository.findById(id)
                .filter(m -> m.getCreatedBy().equals(createdBy))
                .orElse(null);
        if (material == null) return ResponseEntity.notFound().build();
        materialRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
