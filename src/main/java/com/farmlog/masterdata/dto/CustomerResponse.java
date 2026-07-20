package com.farmlog.masterdata.dto;

public record CustomerResponse(Long id, String name, String customerType, String phone, String memo, boolean activeYn) {
}
