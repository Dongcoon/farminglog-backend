package com.farmlog.organizationdashboard;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.organizationdashboard.dto.OrganizationDashboardDtos.*;
import com.farmlog.records.dto.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/organizations/{organizationId}")
public class OrganizationDashboardController {
  private final OrganizationDashboardService service;

  public OrganizationDashboardController(OrganizationDashboardService service) {
    this.service = service;
  }

  @GetMapping("/dashboard")
  public DashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long organizationId, @RequestParam String month, @RequestParam String basis) {
    return service.dashboard(principal.userId(), organizationId, month, basis);
  }

  @GetMapping("/farms")
  public PageResponse<FarmRowResponse> farms(@AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long organizationId, @RequestParam String month, @RequestParam String basis,
      @RequestParam(required=false) String q,
      @RequestParam(required=false) String lifecycleStatus,
      @RequestParam(required=false) String status,
      @RequestParam(defaultValue="ALL") String dataStatus,
      @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="name,asc") String sort) {
    return service.farms(principal.userId(), organizationId,
        new OrganizationFarmFilter(month,basis,q,lifecycleStatus,status,dataStatus,page,size,sort));
  }

  @GetMapping("/farms/{farmId}")
  public FarmDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long organizationId, @PathVariable Long farmId,
      @RequestParam String month, @RequestParam String basis) {
    return service.detail(principal.userId(), organizationId, farmId, month, basis);
  }
}
