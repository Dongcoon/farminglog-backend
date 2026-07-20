package com.farmlog.farmstructure.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Phase 8 구조 변경 API의 중첩 계약을 한 곳에서 고정한다. */
public final class StructureDtos {
  private StructureDtos() {}

  public record SelectedPeriod(LocalDate from, LocalDate toExclusive) {}
  public record Target(String mode, Long farmId, String name, Long ownerUserId) {}
  public record Resolution(String conflictId, String resourceType, String action,
                           Long targetResourceId, Long canonicalSourceResourceId, String newName) {}
  public record MergePreviewRequest(String clientRequestId, List<Long> sourceFarmIds, Target target,
                                    LocalDate effectiveDate, String reportPolicy, SelectedPeriod selectedPeriod,
                                    List<String> masterDataScopes, List<Resolution> masterDataResolutions,
                                    String reason) {}
  public record SplitPreviewRequest(String clientRequestId, Long sourceFarmId, Target target,
                                    List<Long> zoneIds, LocalDate effectiveDate, String reportPolicy,
                                    SelectedPeriod selectedPeriod, List<String> masterDataScopes,
                                    List<Resolution> masterDataResolutions, String reason) {}
  public record ConfirmRequest(String clientRequestId, Long previewEventId, Long previewVersion,
                               String previewHash) {}
  public record CancelRequest(String clientRequestId, Long version, String reason) {}

  public record RecordCounts(long work, long pestControl, long harvest, long sales, long fertilizer) {}
  public record MasterCounts(long cropVariety, long season, long workType, long customer, long material) {}
  public record Impact(int sourceFarmCount, String targetMode, int zoneCount, RecordCounts recordCounts,
                       MasterCounts masterCounts, long memberCopyCount, long memberExcludedCount,
                       long zoneLessSalesCount) {}
  public record ConflictSource(Long farmId, Long resourceId, String name) {}
  public record TargetCandidate(Long resourceId, String name) {}
  public record Conflict(String conflictId, String resourceType, String matchKey,
                         List<ConflictSource> sources, TargetCandidate targetCandidate,
                         List<String> allowedActions, boolean resolutionRequired) {}
  public record Warning(String code, String message, Map<String, Object> meta) {}
  public record PreviewResponse(Long eventId, Long version, String status, Object requestSnapshot,
                                String previewHash, LocalDateTime expiresAt, Impact impact,
                                List<Conflict> conflicts, List<Warning> warnings) {}

  public record OrganizationIdentity(Long id, String name, String orgType, String status) {}
  public record FarmIdentity(Long id, Long organizationId, String farmCode, String name,
                             String lifecycleStatus, String status, Long structureVersion,
                             LocalDateTime createdAt) {}
  public record Owner(Long id, String displayName, String role, String status) {}
  public record Actor(Long id, String displayName) {}
  public record Assignment(Long id, Long farmId, LocalDate effectiveFrom, Long version) {}
  public record Zone(Long id, String name, boolean active, Long version, Assignment assignment) {}
  public record MasterSummary(long cropVariety, long season, long workType, long customer, long material) {}
  public record ContextOrganization(Long id, String name, String orgType, String status,
                                    String capability, long activeOwnedFarmCount) {}
  public record ContextFarm(Long id, Long organizationId, String farmCode, String name, Long ownerUserId,
                            String lifecycleStatus, String status, Long structureVersion,
                            LocalDateTime createdAt, long currentZoneCount) {}
  public record ContextFarmDetail(Long id, Long organizationId, String farmCode, String name, Long ownerUserId,
                                  String lifecycleStatus, String status, Long structureVersion,
                                  LocalDateTime createdAt, long currentZoneCount, List<Owner> owners,
                                  List<Zone> currentZones, MasterSummary masterSummary) {}
  public record EventTarget(String mode, FarmIdentity farm, String proposedName, Long ownerUserId) {}
  public record EventSummary(Long id, Long version, OrganizationIdentity organization, String eventType,
                             String eventName, LocalDate effectiveDate, String status, String reportPolicy,
                             SelectedPeriod selectedPeriod, List<FarmIdentity> sourceFarms, EventTarget targetFarm,
                             Actor createdBy, LocalDateTime createdAt, Actor confirmedBy, LocalDateTime confirmedAt,
                             LocalDateTime expiresAt, String previewHash, boolean cancellable,
                             String cancelBlockedReason, Long reversesEventId, Long reversedByEventId) {}
  public record EventDetail(Long id, Long version, OrganizationIdentity organization, String eventType,
                            String eventName, LocalDate effectiveDate, String status, String reportPolicy,
                            SelectedPeriod selectedPeriod, List<FarmIdentity> sourceFarms, EventTarget targetFarm,
                            Object requestSnapshot, Impact impact, List<Conflict> conflicts, List<Warning> warnings,
                            Actor createdBy, LocalDateTime createdAt, Actor confirmedBy, LocalDateTime confirmedAt,
                            LocalDateTime expiresAt, String previewHash, boolean cancellable,
                            String cancelBlockedReason, Long reversesEventId, Long reversedByEventId,
                            Long targetFarmId, List<Long> affectedFarmIds) {}
}
