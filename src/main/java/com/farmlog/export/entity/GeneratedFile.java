package com.farmlog.export.entity;

import java.nio.file.Path;

public record GeneratedFile(String originalFileName, String storedFileName, String contentType,
                            long fileSize, Path storagePath) {}
