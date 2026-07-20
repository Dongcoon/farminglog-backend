package com.farmlog.admin;

import com.farmlog.admin.dto.*;
import com.farmlog.admin.entity.*;
import com.farmlog.admin.mapper.AdminMapper;
import com.farmlog.common.exception.*;
import com.farmlog.records.dto.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  private static final Set<String> LIFECYCLES = Set.of("ACTIVE", "MERGED", "SPLIT", "ARCHIVED");
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
  private static final Set<String> SENSITIVE =
      Set.of("token", "password", "secret", "path", "memo", "authorization", "cookie", "hash");
  private final AdminMapper mapper;
  private final AdminAccessGuard guard;
  private final ObjectMapper json;

  public AdminService(AdminMapper mapper, AdminAccessGuard guard, ObjectMapper json) {
    this.mapper = mapper;
    this.guard = guard;
    this.json = json;
  }

  @Transactional
  public PageResponse<AdminUserDto> users(
      Long actor, AdminFilters.Users filter, AdminFilters.RequestMeta meta) {
    guard.requireSystemAdmin(actor);
    page(filter.page(), filter.size());
    code(filter.status(), 30, "status");
    positive(filter.organizationId());
    Sort sort =
        sort(
            filter.sort(),
            Map.of(
                "createdAt",
                "u.created_at",
                "lastLoginAt",
                "u.last_login_at",
                "displayName",
                "u.display_name",
                "status",
                "u.status",
                "id",
                "u.id"),
            "u.created_at");
    String q = like(filter.q());
    List<AdminUserDto> content =
        mapper
            .findUsers(
                q,
                filter.status(),
                filter.organizationId(),
                sort.column,
                sort.direction,
                filter.page() * filter.size(),
                filter.size())
            .stream()
            .map(this::user)
            .toList();
    long total = mapper.countUsers(q, filter.status(), filter.organizationId());
    PageResponse<AdminUserDto> result =
        PageResponse.of(content, filter.page(), filter.size(), total);
    audit(
        actor,
        "ADMIN_USERS_VIEW",
        meta,
        details(
            "qPresent",
            q != null,
            "status",
            filter.status(),
            "organizationId",
            filter.organizationId(),
            "page",
            filter.page(),
            "size",
            filter.size(),
            "resultCount",
            content.size()));
    return result;
  }

  @Transactional
  public PageResponse<AdminFarmDto> farms(
      Long actor, AdminFilters.Farms filter, AdminFilters.RequestMeta meta) {
    guard.requireSystemAdmin(actor);
    page(filter.page(), filter.size());
    code(filter.status(), 30, "status");
    if (filter.lifecycleStatus() != null && !LIFECYCLES.contains(filter.lifecycleStatus()))
      throw validation("lifecycleStatus가 올바르지 않습니다.");
    positive(filter.organizationId());
    positive(filter.ownerUserId());
    Sort sort =
        sort(
            filter.sort(),
            Map.of(
                "createdAt",
                "f.created_at",
                "updatedAt",
                "f.updated_at",
                "name",
                "f.name",
                "status",
                "f.status",
                "lifecycleStatus",
                "f.lifecycle_status",
                "id",
                "f.id"),
            "f.created_at");
    String q = like(filter.q());
    List<AdminFarmDto> content =
        mapper
            .findFarms(
                q,
                filter.organizationId(),
                filter.ownerUserId(),
                filter.lifecycleStatus(),
                filter.status(),
                sort.column,
                sort.direction,
                filter.page() * filter.size(),
                filter.size())
            .stream()
            .map(this::farm)
            .toList();
    long total =
        mapper.countFarms(
            q,
            filter.organizationId(),
            filter.ownerUserId(),
            filter.lifecycleStatus(),
            filter.status());
    PageResponse<AdminFarmDto> result =
        PageResponse.of(content, filter.page(), filter.size(), total);
    audit(
        actor,
        "ADMIN_FARMS_VIEW",
        meta,
        details(
            "qPresent",
            q != null,
            "organizationId",
            filter.organizationId(),
            "ownerUserId",
            filter.ownerUserId(),
            "lifecycleStatus",
            filter.lifecycleStatus(),
            "status",
            filter.status(),
            "page",
            filter.page(),
            "size",
            filter.size(),
            "resultCount",
            content.size()));
    return result;
  }

  @Transactional
  public PageResponse<AdminAuditLogDto> auditLogs(
      Long actor, AdminFilters.Audits filter, AdminFilters.RequestMeta meta) {
    guard.requireSystemAdmin(actor);
    page(filter.page(), filter.size());
    positive(filter.organizationId());
    positive(filter.farmId());
    positive(filter.actorUserId());
    positive(filter.targetId());
    code(filter.action(), 80, "action");
    code(filter.targetType(), 80, "targetType");
    if (filter.createdFrom() != null
        && filter.createdToExclusive() != null
        && !filter.createdFrom().isBefore(filter.createdToExclusive()))
      throw validation("createdFrom은 createdToExclusive보다 빨라야 합니다.");
    Sort sort =
        sort(
            filter.sort(),
            Map.of(
                "createdAt",
                "a.created_at",
                "action",
                "a.action",
                "targetType",
                "a.target_type",
                "id",
                "a.id"),
            "a.created_at");
    List<AdminAuditLogDto> content =
        mapper
            .findAuditLogs(
                filter.organizationId(),
                filter.farmId(),
                filter.actorUserId(),
                filter.action(),
                filter.targetType(),
                filter.targetId(),
                filter.createdFrom(),
                filter.createdToExclusive(),
                sort.column,
                sort.direction,
                filter.page() * filter.size(),
                filter.size())
            .stream()
            .map(this::auditDto)
            .toList();
    long total =
        mapper.countAuditLogs(
            filter.organizationId(),
            filter.farmId(),
            filter.actorUserId(),
            filter.action(),
            filter.targetType(),
            filter.targetId(),
            filter.createdFrom(),
            filter.createdToExclusive());
    PageResponse<AdminAuditLogDto> result =
        PageResponse.of(content, filter.page(), filter.size(), total);
    audit(
        actor,
        "ADMIN_AUDIT_LOGS_VIEW",
        meta,
        details(
            "organizationId",
            filter.organizationId(),
            "farmId",
            filter.farmId(),
            "actorUserId",
            filter.actorUserId(),
            "action",
            filter.action(),
            "targetType",
            filter.targetType(),
            "targetId",
            filter.targetId(),
            "createdFrom",
            filter.createdFrom(),
            "createdToExclusive",
            filter.createdToExclusive(),
            "page",
            filter.page(),
            "size",
            filter.size(),
            "resultCount",
            content.size()));
    return result;
  }

  private AdminUserDto user(AdminUserRow r) {
    return new AdminUserDto(
        r.getId(),
        maskEmail(r.getEmail()),
        r.getDisplayName(),
        r.getStatus(),
        r.getActiveOrganizationCount(),
        r.getActiveFarmCount(),
        r.getOwnedActiveFarmCount(),
        r.getLastLoginAt(),
        r.getCreatedAt(),
        r.getDeletedAt());
  }

  private AdminFarmDto farm(AdminFarmRow r) {
    return new AdminFarmDto(
        r.getId(),
        r.getFarmCode(),
        r.getName(),
        new AdminFarmDto.Organization(
            r.getOrganizationId(), r.getOrganizationName(), r.getOrgType()),
        new AdminFarmDto.Owner(r.getOwnerUserId(), r.getOwnerName(), maskEmail(r.getOwnerEmail())),
        maskAddress(r.getAddress()),
        r.getLifecycleStatus(),
        r.getStatus(),
        r.getActiveMemberCount(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }

  private AdminAuditLogDto auditDto(AdminAuditRow r) {
    return new AdminAuditLogDto(
        r.getId(),
        r.getOrganizationId() == null
            ? null
            : new AdminAuditLogDto.Organization(r.getOrganizationId(), r.getOrganizationName()),
        r.getFarmId() == null
            ? null
            : new AdminAuditLogDto.Farm(r.getFarmId(), r.getFarmName()),
        r.getActorUserId() == null
            ? null
            : new AdminAuditLogDto.Actor(
                r.getActorUserId(), r.getActorName(), maskEmail(r.getActorEmail())),
        r.getAction(),
        r.getTargetType(),
        r.getTargetId(),
        maskIp(r.getIpAddress()),
        limit(r.getUserAgent(), 500),
        sanitizeDetail(r.getDetailJson()),
        r.getCreatedAt());
  }

  private void audit(
      Long actor, String action, AdminFilters.RequestMeta meta, Map<String, Object> detail) {
    try {
      mapper.insertViewAudit(
          actor,
          action,
          limit(meta == null ? null : meta.ipAddress(), 80),
          limit(meta == null ? null : meta.userAgent(), 500),
          json.writeValueAsString(detail),
          LocalDateTime.now());
    } catch (Exception e) {
      throw new IllegalStateException("운영자 조회 감사를 기록하지 못했습니다.", e);
    }
  }

  private Map<String, Object> sanitizeDetail(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      Object value = json.readValue(raw, new TypeReference<Object>() {});
      Object clean = sanitize(value, null);
      if (clean instanceof Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
      }
      return Map.of("value", clean);
    } catch (Exception e) {
      return Map.of("value", "[REDACTED]");
    }
  }

  private Object sanitize(Object value, String key) {
    if (value == null) return null;
    String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
    if (SENSITIVE.stream().anyMatch(lower::contains)) return "[REDACTED]";
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      map.forEach((k, v) -> out.put(String.valueOf(k), sanitize(v, String.valueOf(k))));
      return out;
    }
    if (value instanceof List<?> list) return list.stream().map(v -> sanitize(v, key)).toList();
    if (value instanceof String text) {
      if (lower.contains("email")) return maskEmail(text);
      if (lower.contains("ip")) return maskIp(text);
      if (lower.contains("address")) return maskAddress(text);
      if (text.startsWith("eyJ") || text.matches("(?i)^[a-z]:[\\\\/].*") || text.startsWith("/"))
        return "[REDACTED]";
      return limit(text, 500);
    }
    return value;
  }

  private Map<String, Object> details(Object... entries) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2)
      if (entries[i + 1] != null) out.put(String.valueOf(entries[i]), entries[i + 1]);
    return out;
  }

  static String maskEmail(String email) {
    if (email == null || email.isBlank()) return null;
    int at = email.indexOf('@');
    if (at < 1) return "***";
    return email.substring(0, 1) + "***" + email.substring(at);
  }

  static String maskAddress(String address) {
    if (address == null || address.isBlank()) return null;
    String[] parts = address.trim().split("\\s+");
    return String.join(" ", Arrays.copyOf(parts, Math.min(2, parts.length))) + " ***";
  }

  static String maskIp(String ip) {
    if (ip == null || ip.isBlank()) return null;
    if (ip.contains(".")) {
      String[] p = ip.split("\\.");
      return p.length == 4 ? p[0] + "." + p[1] + "." + p[2] + ".***" : "***";
    }
    if (ip.contains(":")) {
      // 축약형 IPv6(::)도 전체 주소를 드러내지 않도록 앞쪽 non-empty hextet 두 개만 보존한다.
      String prefix =
          Arrays.stream(ip.split(":"))
              .filter(part -> !part.isBlank())
              .limit(2)
              .reduce((left, right) -> left + ":" + right)
              .orElse("");
      return prefix.isEmpty() ? "::****" : prefix + "::****";
    }
    return "***";
  }

  private String like(String q) {
    if (q == null || q.isBlank()) return null;
    String value = q.trim().toLowerCase(Locale.ROOT);
    if (value.length() > 100) throw validation("q는 100자 이하입니다.");
    return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  private Sort sort(String raw, Map<String, String> allowed, String fallback) {
    if (raw == null || raw.isBlank()) return new Sort(fallback, "DESC");
    String[] p = raw.split(",", -1);
    String column = p.length == 2 ? allowed.get(p[0]) : null;
    String direction = p.length == 2 ? p[1].toUpperCase(Locale.ROOT) : "";
    if (column == null || !Set.of("ASC", "DESC").contains(direction))
      throw validation("지원하지 않는 정렬입니다.");
    return new Sort(column, direction);
  }

  private void page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw validation("page는 0 이상, size는 1~100입니다.");
  }

  private void positive(Long value) {
    if (value != null && value < 1) throw validation("식별자는 양수여야 합니다.");
  }

  private void code(String value, int max, String name) {
    if (value != null && (value.length() > max || !CODE.matcher(value).matches()))
      throw validation(name + "가 올바르지 않습니다.");
  }

  private String limit(String value, int max) {
    return value == null ? null : value.substring(0, Math.min(value.length(), max));
  }

  private BusinessException validation(String message) {
    return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
  }

  private record Sort(String column, String direction) {}
}
