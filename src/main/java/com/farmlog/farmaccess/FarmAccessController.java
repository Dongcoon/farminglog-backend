package com.farmlog.farmaccess;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.farmaccess.dto.*;
import com.farmlog.records.dto.FeedResponse;
import com.farmlog.records.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class FarmAccessController {
  private final FarmAccessService service;

  public FarmAccessController(FarmAccessService service) {
    this.service = service;
  }

  @GetMapping("/farms/{farmId}/members")
  public PageResponse<MemberDto> members(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.members(p.userId(), farmId, page, size);
  }

  @PatchMapping("/farms/{farmId}/members/{id}")
  public MemberDto update(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @Valid @RequestBody MemberUpdateRequest req) {
    return service.updateMember(p.userId(), farmId, id, req);
  }

  @GetMapping("/farms/{farmId}/invitations")
  public PageResponse<InvitationDto> invitations(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.invitations(p.userId(), farmId, status, page, size);
  }

  @PostMapping("/farms/{farmId}/invitations")
  @ResponseStatus(HttpStatus.CREATED)
  public InvitationDto invite(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @Valid @RequestBody InvitationRequests.Create req) {
    return service.createInvitation(p.userId(), farmId, req);
  }

  @PatchMapping("/farms/{farmId}/invitations/{id}")
  public InvitationDto cancel(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @Valid @RequestBody InvitationRequests.Cancel req) {
    return service.cancelInvitation(p.userId(), farmId, id, req);
  }

  @PostMapping("/farm-invitations/{token}/accept")
  public MemberDto accept(@AuthenticationPrincipal UserPrincipal p, @PathVariable String token) {
    return service.acceptInvitation(p.userId(), p.email(), token);
  }

  @GetMapping("/farms/{farmId}/care-assignments")
  public PageResponse<CareAssignmentDto> farmCare(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.farmAssignments(p.userId(), farmId, status, page, size);
  }

  @PostMapping("/farms/{farmId}/care-assignments")
  @ResponseStatus(HttpStatus.CREATED)
  public CareAssignmentDto assign(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @Valid @RequestBody CareAssignmentRequests.Create req) {
    return service.createAssignment(p.userId(), farmId, req);
  }

  @PatchMapping("/farms/{farmId}/care-assignments/{id}")
  public CareAssignmentDto revoke(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @Valid @RequestBody CareAssignmentRequests.Revoke req) {
    return service.revokeAssignment(p.userId(), farmId, id, req);
  }

  @GetMapping("/farms/{farmId}/care-assignments/{id}/recent-records")
  public List<FeedResponse> recent(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "5") int size) {
    return service.recentRecords(p.userId(), farmId, id, size);
  }

  /** 일반 사용자에게도 빈 PageResponse를 반환해 "권한 없음"과 "배정 없음"을 구분한다. */
  @GetMapping("/farm-care/assignments")
  public PageResponse<CareAssignmentDto> myCare(
      @AuthenticationPrincipal UserPrincipal p,
      @RequestParam(defaultValue = "ACTIVE") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.myAssignments(p.userId(), status, page, size);
  }
}
