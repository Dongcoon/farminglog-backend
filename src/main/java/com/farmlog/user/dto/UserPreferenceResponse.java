package com.farmlog.user.dto;

public record UserPreferenceResponse(
        String viewScale,
        boolean outdoorMode,
        boolean reduceMotion,
        Long lastSelectedFarmId
) {
}
