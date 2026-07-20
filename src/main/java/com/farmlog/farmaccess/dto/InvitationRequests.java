package com.farmlog.farmaccess.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class InvitationRequests {
  private InvitationRequests() {}

  public record Create(
      @NotBlank String clientRequestId, @Email @NotBlank String email, @NotBlank String role) {}

  public record Cancel(@NotBlank String status, @NotNull Long version) {}
}
