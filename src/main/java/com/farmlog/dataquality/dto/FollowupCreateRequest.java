package com.farmlog.dataquality.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record FollowupCreateRequest(
    @NotBlank String clientRequestId,
    @NotBlank String contactMethod,
    @NotBlank String followupStatus,
    @NotBlank @Size(max = 2000) String contentSummary,
    LocalDateTime nextActionAt) {}
