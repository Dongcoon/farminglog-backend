package com.farmlog.attachment.dto;

import java.nio.file.Path;

public record AttachmentDownload(Path path, String fileName, String contentType, long fileSize) {}
