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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
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
    public ResponseEntity<Material> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID createdBy = UUID.fromString(jwt.getSubject());
        return materialRepository.findById(id)
                .filter(m -> m.getCreatedBy().equals(createdBy))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        return materialRepository.findById(id)
                .map(m -> {
                    m.setIsPublished(true);
                    return ResponseEntity.ok(materialRepository.save(m));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('client_admin')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID createdBy = UUID.fromString(jwt.getSubject());
        return materialRepository.findById(id)
                .filter(m -> m.getCreatedBy().equals(createdBy))
                .map(m -> {
                    materialRepository.deleteById(id);
                    return ResponseEntity.<Void>noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
