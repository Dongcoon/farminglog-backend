package com.farmlog.farmaccess.dto;

import jakarta.validation.constraints.NotNull;

public record MemberUpdateRequest(String role, String status, @NotNull Long version) {}
