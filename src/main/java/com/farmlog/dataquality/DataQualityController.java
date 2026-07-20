package com.farmlog.dataquality;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.dataquality.dto.*;
import com.farmlog.records.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/farms/{farmId}/data-quality/issues")
public class DataQualityController {
  private final DataQualityService service;

  public DataQualityController(DataQualityService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<DataQualityIssueDto> list(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @RequestParam(required = false) String issueType,
      @RequestParam(required = false) String issueStatus,
      @RequestParam(required = false) String severity,
      @RequestParam(required = false) String farmerConfirmStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return service.list(
        p.userId(),
        farmId,
        issueType,
        issueStatus,
        severity,
        farmerConfirmStatus,
        page,
        size,
        sort);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DataQualityIssueDto create(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @Valid @RequestBody IssueRequests.Create req) {
    return service.create(p.userId(), farmId, req);
  }

  @GetMapping("/{id}")
  public DataQualityIssueDto detail(
      @AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId, @PathVariable Long id) {
    return service.detail(p.userId(), farmId, id);
  }

  @PatchMapping("/{id}")
  public DataQualityIssueDto update(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @Valid @RequestBody IssueRequests.Update req) {
    return service.update(p.userId(), farmId, id, req);
  }

  @GetMapping("/{id}/followups")
  public PageResponse<FollowupDto> followups(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.followups(p.userId(), farmId, id, page, size);
  }

  @PostMapping("/{id}/followups")
  @ResponseStatus(HttpStatus.CREATED)
  public FollowupDto followup(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable Long id,
      @Valid @RequestBody FollowupCreateRequest req) {
    return service.createFollowup(p.userId(), farmId, id, req);
  }
}
