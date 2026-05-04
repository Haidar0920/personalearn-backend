package com.example.personalearn.repository;

import com.example.personalearn.entity.AIMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AIMessageRepository extends JpaRepository<AIMessage, UUID> {

    List<AIMessage> findByEmployeeIdAndChatTypeOrderByCreatedAtAsc(UUID employeeId, String chatType);

    List<AIMessage> findByEmployeeIdAndMaterialIdOrderByCreatedAtAsc(UUID employeeId, UUID materialId);

    List<AIMessage> findByUserIdAndChatTypeOrderByCreatedAtAsc(UUID userId, String chatType);

    long countByEmployeeId(UUID employeeId);
}
