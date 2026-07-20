package com.farmlog.masterdata.dto;

public record MaterialResponse(Long id, String name, String materialType, String unit, String memo, boolean activeYn) {
}
