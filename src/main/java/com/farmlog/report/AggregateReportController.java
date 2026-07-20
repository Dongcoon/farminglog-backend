package com.farmlog.report;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.report.dto.AggregateMonthlyReportResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/farms/reports")
public class AggregateReportController {
  private final ReportService service;

  public AggregateReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/monthly/aggregate")
  public AggregateMonthlyReportResponse aggregate(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam String month,
      @RequestParam String basis) {
    return service.aggregate(principal.userId(), month, basis);
  }
}
