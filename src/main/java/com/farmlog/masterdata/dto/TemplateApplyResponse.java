package com.farmlog.masterdata.dto;

public record TemplateApplyResponse(String templateCode, Counts created, Counts reused) {
    public record Counts(int crops, int varieties, int workTypes) {
    }
}
