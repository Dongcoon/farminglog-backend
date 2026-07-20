package com.farmlog.masterdata;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.masterdata.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** W-04 기준정보 관리와 M-04 딸기 초기 템플릿 API. */
@RestController
public class MasterDataController {
    private final MasterDataService service;

    public MasterDataController(MasterDataService service) { this.service = service; }

    @GetMapping("/farms/{farmId}/crops")
    public List<CatalogResponse> crops(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                       @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listCrops(p.userId(), farmId, includeInactive);
    }
    @PostMapping("/farms/{farmId}/crops") @ResponseStatus(HttpStatus.CREATED)
    public CatalogResponse createCrop(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                      @Valid @RequestBody CatalogCreateRequest request) {
        return service.createCrop(p.userId(), farmId, request);
    }
    @PatchMapping("/farms/{farmId}/crops/{cropId}")
    public CatalogResponse updateCrop(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                      @PathVariable Long cropId, @Valid @RequestBody CatalogUpdateRequest request) {
        return service.updateCrop(p.userId(), farmId, cropId, request);
    }
    @PatchMapping("/farms/{farmId}/crops/{cropId}/deactivate")
    public CatalogResponse deactivateCrop(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                          @PathVariable Long cropId) {
        return service.deactivateCrop(p.userId(), farmId, cropId);
    }

    @GetMapping("/farms/{farmId}/crops/{cropId}/varieties")
    public List<CatalogResponse> varieties(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @PathVariable Long cropId,
                                           @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listVarieties(p.userId(), farmId, cropId, includeInactive);
    }
    @PostMapping("/farms/{farmId}/crops/{cropId}/varieties") @ResponseStatus(HttpStatus.CREATED)
    public CatalogResponse createVariety(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                         @PathVariable Long cropId, @Valid @RequestBody CatalogCreateRequest request) {
        return service.createVariety(p.userId(), farmId, cropId, request);
    }
    @PatchMapping("/farms/{farmId}/crops/{cropId}/varieties/{varietyId}")
    public CatalogResponse updateVariety(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                         @PathVariable Long cropId, @PathVariable Long varietyId,
                                         @Valid @RequestBody CatalogUpdateRequest request) {
        return service.updateVariety(p.userId(), farmId, cropId, varietyId, request);
    }
    @PatchMapping("/farms/{farmId}/crops/{cropId}/varieties/{varietyId}/deactivate")
    public CatalogResponse deactivateVariety(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                             @PathVariable Long cropId, @PathVariable Long varietyId) {
        return service.deactivateVariety(p.userId(), farmId, cropId, varietyId);
    }

    @GetMapping("/farms/{farmId}/crop-seasons")
    public List<CropSeasonResponse> seasons(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                            @RequestParam(defaultValue = "false") boolean includeCompleted) {
        return service.listCropSeasons(p.userId(), farmId, includeCompleted);
    }
    @PostMapping("/farms/{farmId}/crop-seasons") @ResponseStatus(HttpStatus.CREATED)
    public CropSeasonResponse createSeason(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @Valid @RequestBody CropSeasonCreateRequest request) {
        return service.createCropSeason(p.userId(), farmId, request);
    }
    @PatchMapping("/farms/{farmId}/crop-seasons/{seasonId}")
    public CropSeasonResponse updateSeason(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @PathVariable Long seasonId,
                                           @Valid @RequestBody CropSeasonUpdateRequest request) {
        return service.updateCropSeason(p.userId(), farmId, seasonId, request);
    }
    @PatchMapping("/farms/{farmId}/crop-seasons/{seasonId}/complete")
    public CropSeasonResponse completeSeason(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                             @PathVariable Long seasonId) {
        return service.completeCropSeason(p.userId(), farmId, seasonId);
    }
    @DeleteMapping("/farms/{farmId}/crop-seasons/{seasonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSeason(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                             @PathVariable Long seasonId) {
        service.deleteCropSeason(p.userId(), farmId, seasonId);
    }

    @GetMapping("/farms/{farmId}/work-types")
    public List<WorkTypeResponse> workTypes(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listWorkTypes(p.userId(), farmId, includeInactive);
    }
    @PostMapping("/farms/{farmId}/work-types") @ResponseStatus(HttpStatus.CREATED)
    public WorkTypeResponse createWorkType(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @Valid @RequestBody WorkTypeCreateRequest request) {
        return service.createWorkType(p.userId(), farmId, request);
    }
    @PatchMapping("/farms/{farmId}/work-types/{id}")
    public WorkTypeResponse updateWorkType(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @PathVariable Long id, @Valid @RequestBody WorkTypeUpdateRequest request) {
        return service.updateWorkType(p.userId(), farmId, id, request);
    }
    @PatchMapping("/farms/{farmId}/work-types/{id}/deactivate")
    public WorkTypeResponse deactivateWorkType(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                               @PathVariable Long id) {
        return service.deactivateWorkType(p.userId(), farmId, id);
    }

    @GetMapping("/farms/{farmId}/customers")
    public List<CustomerResponse> customers(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listCustomers(p.userId(), farmId, includeInactive);
    }
    @PostMapping("/farms/{farmId}/customers") @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @Valid @RequestBody CustomerCreateRequest request) {
        return service.createCustomer(p.userId(), farmId, request);
    }
    @PatchMapping("/farms/{farmId}/customers/{id}")
    public CustomerResponse updateCustomer(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
        return service.updateCustomer(p.userId(), farmId, id, request);
    }
    @PatchMapping("/farms/{farmId}/customers/{id}/deactivate")
    public CustomerResponse deactivateCustomer(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                               @PathVariable Long id) {
        return service.deactivateCustomer(p.userId(), farmId, id);
    }

    @GetMapping("/farms/{farmId}/materials")
    public List<MaterialResponse> materials(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listMaterials(p.userId(), farmId, includeInactive);
    }
    @PostMapping("/farms/{farmId}/materials") @ResponseStatus(HttpStatus.CREATED)
    public MaterialResponse createMaterial(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @Valid @RequestBody MaterialCreateRequest request) {
        return service.createMaterial(p.userId(), farmId, request);
    }
    @PatchMapping("/farms/{farmId}/materials/{id}")
    public MaterialResponse updateMaterial(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                           @PathVariable Long id, @Valid @RequestBody MaterialUpdateRequest request) {
        return service.updateMaterial(p.userId(), farmId, id, request);
    }
    @PatchMapping("/farms/{farmId}/materials/{id}/deactivate")
    public MaterialResponse deactivateMaterial(@AuthenticationPrincipal UserPrincipal p, @PathVariable Long farmId,
                                               @PathVariable Long id) {
        return service.deactivateMaterial(p.userId(), farmId, id);
    }

    @GetMapping("/master-data/templates/STRAWBERRY")
    public TemplatePreviewResponse strawberryTemplate() { return service.previewStrawberryTemplate(); }

    @PostMapping("/farms/{farmId}/master-data/templates/STRAWBERRY/apply")
    public TemplateApplyResponse applyStrawberryTemplate(@AuthenticationPrincipal UserPrincipal p,
                                                         @PathVariable Long farmId) {
        return service.applyStrawberryTemplate(p.userId(), farmId);
    }
}
