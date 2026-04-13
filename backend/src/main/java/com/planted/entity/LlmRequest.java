package com.planted.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "llm_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt_template_id")
    private Long promptTemplateId;

    @Column(name = "prompt_key", nullable = false)
    private String promptKey;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "rendered_prompt", nullable = false, columnDefinition = "TEXT")
    private String renderedPrompt;

    @Type(JsonBinaryType.class)
    @Column(name = "input_variables", columnDefinition = "jsonb")
    private Map<String, Object> inputVariables;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "plant_id")
    private Long plantId;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
