package com.farmlog.farmstructure;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.farmstructure.dto.StructureDtos.*;
import com.farmlog.records.dto.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/farm-structure-context")
public class FarmStructureContextController {
  private final FarmStructureContextService service;
  public FarmStructureContextController(FarmStructureContextService service){this.service=service;}
  @GetMapping("/organizations")
  public PageResponse<ContextOrganization> organizations(@AuthenticationPrincipal UserPrincipal p,
      @RequestParam(required=false) Long organizationId,@RequestParam(required=false) String q,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.organizations(p.userId(),organizationId,q,page,size);}
  @GetMapping("/organizations/{organizationId}/farms")
  public PageResponse<ContextFarm> farms(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long organizationId,
      @RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.farms(p.userId(),organizationId,q,page,size);}
  @GetMapping("/organizations/{organizationId}/farms/{farmId}")
  public ContextFarmDetail farm(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long organizationId,@PathVariable Long farmId){return service.farm(p.userId(),organizationId,farmId);}
}
