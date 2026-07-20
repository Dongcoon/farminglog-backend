package com.farmlog.admin;

import com.farmlog.admin.dto.*;
import com.farmlog.common.security.UserPrincipal;
import com.farmlog.records.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {
  private final AdminService service;

  public AdminController(AdminService service) {
    this.service = service;
  }

  @GetMapping("/users")
  public PageResponse<AdminUserDto> users(
      @AuthenticationPrincipal UserPrincipal p,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long organizationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      HttpServletRequest request) {
    return service.users(
        p.userId(),
        new AdminFilters.Users(q, status, organizationId, page, size, sort),
        meta(request));
  }

  @GetMapping("/farms")
  public PageResponse<AdminFarmDto> farms(
      @AuthenticationPrincipal UserPrincipal p,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long organizationId,
      @RequestParam(required = false) Long ownerUserId,
      @RequestParam(required = false) String lifecycleStatus,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      HttpServletRequest request) {
    return service.farms(
        p.userId(),
        new AdminFilters.Farms(
            q, organizationId, ownerUserId, lifecycleStatus, status, page, size, sort),
        meta(request));
  }

  @GetMapping("/audit-logs")
  public PageResponse<AdminAuditLogDto> audits(
      @AuthenticationPrincipal UserPrincipal p,
      @RequestParam(required = false) Long organizationId,
      @RequestParam(required = false) Long farmId,
      @RequestParam(required = false) Long actorUserId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) Long targetId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime createdToExclusive,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      HttpServletRequest request) {
    return service.auditLogs(
        p.userId(),
        new AdminFilters.Audits(
            organizationId,
            farmId,
            actorUserId,
            action,
            targetType,
            targetId,
            createdFrom,
            createdToExclusive,
            page,
            size,
            sort),
        meta(request));
  }

  private AdminFilters.RequestMeta meta(HttpServletRequest request) {
    return new AdminFilters.RequestMeta(request.getRemoteAddr(), request.getHeader("User-Agent"));
  }
}
