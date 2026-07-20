package com.farmlog.organizationdashboard;

public record OrganizationFarmFilter(
    String month, String basis, String q, String lifecycleStatus, String status, String dataStatus,
    int page, int size, String sort) {}
