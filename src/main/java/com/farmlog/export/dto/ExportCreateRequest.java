package com.farmlog.export.dto;

import com.farmlog.export.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ExportCreateRequest(@NotBlank String clientRequestId, @NotNull ExportFormat format,
                                  @NotNull LocalDate dateFrom, @NotNull LocalDate dateTo,
                                  @NotEmpty @Size(max = 5) List<@NotNull ExportScope> scopes,
                                  @NotNull ConfirmationFilter confirmationFilter) {}
