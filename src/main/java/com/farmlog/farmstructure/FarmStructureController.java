package com.farmlog.farmstructure;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.farmstructure.dto.StructureDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.farmlog.records.dto.PageResponse;
import java.time.LocalDate;

@RestController
@RequestMapping("/farm-structure-events")
public class FarmStructureController {
  private final FarmStructureService service;
  public FarmStructureController(FarmStructureService service){this.service=service;}
  @GetMapping
  public PageResponse<EventSummary> events(@AuthenticationPrincipal UserPrincipal p,
      @RequestParam Long organizationId,@RequestParam(required=false) String eventType,
      @RequestParam(required=false) String status,@RequestParam(required=false) Long sourceFarmId,
      @RequestParam(required=false) Long targetFarmId,@RequestParam(required=false) LocalDate effectiveFrom,
      @RequestParam(required=false) LocalDate effectiveToExclusive,@RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="effectiveDate,desc") String sort){
    return service.events(p.userId(),organizationId,eventType,status,sourceFarmId,targetFarmId,effectiveFrom,effectiveToExclusive,page,size,sort);
  }
  @PostMapping("/merge/preview") @ResponseStatus(HttpStatus.CREATED)
  public PreviewResponse mergePreview(@AuthenticationPrincipal UserPrincipal p,@RequestBody MergePreviewRequest request){return service.previewMerge(p.userId(),request);}
  @PostMapping("/split/preview") @ResponseStatus(HttpStatus.CREATED)
  public PreviewResponse splitPreview(@AuthenticationPrincipal UserPrincipal p,@RequestBody SplitPreviewRequest request){return service.previewSplit(p.userId(),request);}
  @PostMapping("/merge")
  public EventDetail merge(@AuthenticationPrincipal UserPrincipal p,@RequestBody ConfirmRequest request){return service.confirm(p.userId(),"MERGE",request);}
  @PostMapping("/split")
  public EventDetail split(@AuthenticationPrincipal UserPrincipal p,@RequestBody ConfirmRequest request){return service.confirm(p.userId(),"SPLIT",request);}
  @PostMapping("/{eventId}/cancel")
  public EventDetail cancel(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long eventId,@RequestBody CancelRequest request){return service.cancel(p.userId(),eventId,request);}
  @GetMapping("/{eventId}")
  public EventDetail detail(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long eventId){return service.detail(p.userId(),eventId);}
}
