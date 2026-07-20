package com.farmlog.records.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FarmerConfirmationRequest(@NotBlank String status, @NotNull Long version) {}
