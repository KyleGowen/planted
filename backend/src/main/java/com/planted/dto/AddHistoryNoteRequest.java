package com.planted.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddHistoryNoteRequest(
        @NotBlank @Size(max = 180) String noteText
) {}
