package com.farmlog.report;

import com.farmlog.common.security.UserPrincipal;
import com.farmlog.report.dto.MonthlyReportResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farms/{farmId}/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/monthly")
    public MonthlyReportResponse monthly(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long farmId,
                                         @RequestParam String month, @RequestParam String basis,
                                         @RequestParam(required = false) Long eventId) {
        return service.monthly(principal.userId(), farmId, month, basis, eventId);
    }
}
