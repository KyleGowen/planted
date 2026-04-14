package com.planted.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserLocationRequest(
        @Size(max = 1000, message = "Address must be at most 1000 characters")
        String address
) {
}
