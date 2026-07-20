package com.farmlog.attachment;

public record ImageInspection(
    String extension, String contentType, int width, int height, byte[] bytes) {}
