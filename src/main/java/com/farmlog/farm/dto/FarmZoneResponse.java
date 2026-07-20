package com.farmlog.farm.dto;

import java.math.BigDecimal;

public record FarmZoneResponse(
        Long id,
        String name,
        String zoneType,
        BigDecimal areaValue,
        String areaUnit,
        Integer displayOrder,
        boolean activeYn
) {
}
