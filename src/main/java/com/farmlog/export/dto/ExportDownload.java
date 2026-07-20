package com.farmlog.export.dto;

import java.nio.file.Path;

/** storagePath를 API JSON에 노출하지 않고 controller 내부 전달에만 사용하는 값. */
public record ExportDownload(Path path, String fileName, String contentType, long fileSize) {}
