package com.farmlog.farm;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.farm.dto.FarmCreateRequest;
import com.farmlog.farm.dto.FarmResponse;
import com.farmlog.farm.dto.FarmZoneAssignmentResponse;
import com.farmlog.farm.dto.FarmZoneCreateRequest;
import com.farmlog.farm.dto.FarmZoneResponse;
import com.farmlog.farm.dto.FarmZoneUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 농장/구역(하우스) 설정 API(Phase 2 — M-04 첫 농장 설정 마법사, M-14 농장 선택/전환, W-04 기준정보
 * 관리의 하우스/구역 탭). {@code farmId}가 포함된 모든 엔드포인트는 {@link FarmService} 내부에서
 * {@code common/tenant}의 {@code FarmAccessGuard}로 소속 검증을 거친다.
 */
@RestController
@RequestMapping("/farms")
public class FarmController {

    private final FarmService farmService;

    public FarmController(FarmService farmService) {
        this.farmService = farmService;
    }

    @GetMapping
    public List<FarmResponse> getMyFarms(@AuthenticationPrincipal UserPrincipal principal) {
        return farmService.listMyFarms(principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmResponse createFarm(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody FarmCreateRequest request) {
        return farmService.createFarm(principal.userId(), request);
    }

    @GetMapping("/{farmId}/zones")
    public List<FarmZoneResponse> getZones(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long farmId,
                                            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return farmService.listZones(principal.userId(), farmId, includeInactive);
    }

    @PostMapping("/{farmId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public FarmZoneResponse createZone(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long farmId,
                                        @Valid @RequestBody FarmZoneCreateRequest request) {
        return farmService.createZone(principal.userId(), farmId, request);
    }

    @PatchMapping("/{farmId}/zones/{zoneId}")
    public FarmZoneResponse updateZone(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable Long farmId,
                                        @PathVariable Long zoneId,
                                        @Valid @RequestBody FarmZoneUpdateRequest request) {
        return farmService.updateZone(principal.userId(), farmId, zoneId, request);
    }

    @PatchMapping("/{farmId}/zones/{zoneId}/deactivate")
    public FarmZoneResponse deactivateZone(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long farmId,
                                            @PathVariable Long zoneId) {
        return farmService.deactivateZone(principal.userId(), farmId, zoneId);
    }

    @GetMapping("/{farmId}/zone-assignments")
    public List<FarmZoneAssignmentResponse> getZoneAssignments(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable Long farmId) {
        return farmService.listZoneAssignments(principal.userId(), farmId);
    }
}
