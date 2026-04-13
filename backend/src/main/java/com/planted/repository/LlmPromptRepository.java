package com.planted.repository;

import com.planted.entity.LlmPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LlmPromptRepository extends JpaRepository<LlmPrompt, Long> {

    List<LlmPrompt> findByPromptKeyAndIsActiveTrueOrderByVersionDesc(String promptKey);

    Optional<LlmPrompt> findFirstByPromptKeyAndRoleAndIsActiveTrueOrderByVersionDesc(
            String promptKey, String role);
}
