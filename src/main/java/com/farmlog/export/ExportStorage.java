package com.farmlog.export;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** 설정된 export root 밖으로 경로가 이탈하지 못하도록 모든 파일 접근을 중앙화한다. */
@Component
public class ExportStorage {
    private final Path root;

    public ExportStorage(ExportProperties properties) {
        Path configured = Path.of(properties.getRootDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(configured);
            root = configured.toRealPath();
        }
        catch (IOException ex) { throw new IllegalStateException("내보내기 저장 경로를 준비할 수 없습니다.", ex); }
    }

    public PendingFile pending(Long farmId, String extension) throws IOException {
        Path directory = safe(root.resolve(String.valueOf(farmId)));
        Files.createDirectories(directory);
        directory = realInsideRoot(directory);
        String stored = UUID.randomUUID() + "." + extension;
        Path finalPath = safe(directory.resolve(stored));
        Path tempPath = safe(directory.resolve(stored + ".tmp"));
        return new PendingFile(tempPath, finalPath, root.relativize(finalPath).toString().replace('\\', '/'), stored);
    }

    public void publish(PendingFile pending) throws IOException {
        try { Files.move(pending.tempPath(), pending.finalPath(), StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(pending.tempPath(), pending.finalPath(), StandardCopyOption.REPLACE_EXISTING); }
    }

    public Path resolveExisting(String relativePath) {
        if (relativePath == null) throw new BusinessException(ErrorCode.EXPORT_FILE_MISSING);
        Path resolved = safe(root.resolve(relativePath));
        if (!Files.isRegularFile(resolved)) throw new BusinessException(ErrorCode.EXPORT_FILE_MISSING);
        try { return realInsideRoot(resolved); }
        catch (IOException ex) { throw new BusinessException(ErrorCode.EXPORT_FILE_MISSING); }
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null) return;
        try { Files.deleteIfExists(safe(root.resolve(relativePath))); }
        catch (IOException ignored) { /* 만료 상태는 유지하고 다음 운영 정리 대상으로 남긴다. */ }
    }

    private Path safe(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new BusinessException(ErrorCode.EXPORT_FILE_MISSING);
        return normalized;
    }

    /** 심볼릭 링크까지 해석한 실제 경로도 export root 안에 있는지 확인한다. */
    private Path realInsideRoot(Path path) throws IOException {
        Path real = path.toRealPath();
        if (!real.startsWith(root)) throw new BusinessException(ErrorCode.EXPORT_FILE_MISSING);
        return real;
    }

    public record PendingFile(Path tempPath, Path finalPath, String relativePath, String storedFileName) {}
}
