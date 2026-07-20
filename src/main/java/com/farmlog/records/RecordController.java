package com.farmlog.records;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.records.dto.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** W-03과 M-05~M-11이 공유하는 네 기록 및 통합 피드 API. */
@RestController
@RequestMapping("/farms/{farmId}")
public class RecordController {
  private final RecordService service;

  public RecordController(RecordService service) {
    this.service = service;
  }

  @GetMapping("/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}")
  public PageResponse<RecordResponse> list(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) Long zoneId,
      @RequestParam(required = false) Long cropId,
      @RequestParam(required = false) Long varietyId,
      @RequestParam(required = false) Long seasonId,
      @RequestParam(required = false) Long createdBy,
      @RequestParam(required = false) Long workTypeId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) String settlementStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return service.list(
        p.userId(),
        farmId,
        RecordType.fromPath(domain),
        dateFrom,
        dateTo,
        zoneId,
        cropId,
        varietyId,
        seasonId,
        createdBy,
        workTypeId,
        customerId,
        settlementStatus,
        page,
        size,
        sort);
  }

  @GetMapping("/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}/{id}")
  public RecordResponse detail(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @PathVariable Long id) {
    return service.detail(p.userId(), farmId, RecordType.fromPath(domain), id);
  }

  @PostMapping("/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}")
  @ResponseStatus(HttpStatus.CREATED)
  public RecordResponse create(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @RequestBody RecordMutationRequest request) {
    return service.create(p.userId(), farmId, RecordType.fromPath(domain), request);
  }

  @PatchMapping("/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}/{id}")
  public RecordResponse update(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @PathVariable Long id,
      @RequestBody RecordMutationRequest request) {
    return service.update(p.userId(), farmId, RecordType.fromPath(domain), id, request);
  }

  @DeleteMapping("/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @PathVariable Long id,
      @RequestParam Long version) {
    service.delete(p.userId(), farmId, RecordType.fromPath(domain), id, version);
  }

  @PatchMapping(
      "/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}/{id}/farmer-confirmation")
  public RecordResponse confirm(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @PathVariable Long id,
      @Valid @RequestBody FarmerConfirmationRequest request) {
    return service.farmerConfirmation(p.userId(), farmId, RecordType.fromPath(domain), id, request);
  }

  @PatchMapping("/{domain:work-logs|pest-control-logs|harvest-logs|sales-logs}/bulk")
  public BulkResponse bulk(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @PathVariable String domain,
      @Valid @RequestBody BulkRequest request) {
    return service.bulk(p.userId(), farmId, RecordType.fromPath(domain), request);
  }

  @GetMapping("/records")
  public PageResponse<FeedResponse> feed(
      @AuthenticationPrincipal UserPrincipal p,
      @PathVariable Long farmId,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) Long zoneId,
      @RequestParam(required = false) Long createdBy,
      @RequestParam(required = false) List<RecordType> types,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return service.feed(
        p.userId(), farmId, dateFrom, dateTo, zoneId, createdBy, types, page, size, sort);
  }

  @GetMapping("/record-authors")
  public List<RecordResponse.Actor> authors(
      @AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId) {
    return service.authors(p.userId(), farmId);
  }
}
