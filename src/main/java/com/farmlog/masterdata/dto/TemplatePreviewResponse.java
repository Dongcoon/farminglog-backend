package com.farmlog.masterdata.dto;

import java.util.List;

public record TemplatePreviewResponse(String templateCode, CropTemplate crop, List<String> workTypes) {
    public record CropTemplate(String name, List<String> varieties) {
    }
}
