package com.farmlog.farmaccess.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public final class CareAssignmentRequests {
  private CareAssignmentRequests() {}

  public record Create(
      @NotBlank String clientRequestId,
      @Email @NotBlank String managerEmail,
      String memo,
      LocalDateTime startsAt,
      LocalDateTime endsAt) {}

  public record Revoke(@NotBlank String status, @NotNull Long version, String reason) {}
}
