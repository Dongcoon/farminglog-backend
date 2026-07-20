package com.farmlog.attachment;

import com.farmlog.common.exception.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AttachmentStorage {
  private final Path root;

  public AttachmentStorage(@Value("${farmlog.file.upload-dir:./uploads}") String configured) {
    try {
      Path path = Path.of(configured).toAbsolutePath().normalize();
      Files.createDirectories(path);
      root = path.toRealPath();
    } catch (IOException e) {
      throw new IllegalStateException("첨부 저장소를 준비할 수 없습니다.", e);
    }
  }

  public Stored writeFinal(Long farm, String ext, byte[] bytes) throws IOException {
    return write(root.resolve("attachments").resolve(String.valueOf(farm)), ext, bytes);
  }

  public Stored writeStaging(Long user, Long farm, String batch, String ext, byte[] bytes)
      throws IOException {
    return write(
        root.resolve("staging")
            .resolve(String.valueOf(user))
            .resolve(String.valueOf(farm))
            .resolve(batch),
        ext,
        bytes);
  }

  private Stored write(Path directory, String ext, byte[] bytes) throws IOException {
    Path safe = safe(directory);
    Files.createDirectories(safe);
    safe = real(safe);
    String stored = UUID.randomUUID() + "." + ext;
    Path temp = safe.resolve(stored + ".tmp"), target = safe.resolve(stored);
    boolean success = false;
    try {
      Files.write(temp, bytes, StandardOpenOption.CREATE_NEW);
      moveIntoPlace(temp, target);
      success = true;
      return new Stored(stored, root.relativize(target).toString().replace('\\', '/'));
    } finally {
      deleteCompensation(temp);
      if (!success) deleteCompensation(target);
    }
  }

  /** 원자 이동 미지원 파일시스템에서는 일반 이동으로 폴백한다. */
  void moveIntoPlace(Path temp, Path target) throws IOException {
    try {
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temp, target);
    }
  }

  private void deleteCompensation(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
    }
  }

  public String promote(String staging, Long farm) throws IOException {
    Path source = existing(staging);
    String ext = source.getFileName().toString().replaceFirst("^.*\\.", "");
    Stored target = writeFinal(farm, ext, Files.readAllBytes(source));
    return target.relativePath();
  }

  public Path existing(String relative) {
    if (relative == null) throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
    try {
      Path path = safe(root.resolve(relative));
      if (!Files.isRegularFile(path)) throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
      return real(path);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
    }
  }

  public void deleteQuietly(String relative) {
    if (relative == null) return;
    try {
      Files.deleteIfExists(safe(root.resolve(relative)));
    } catch (IOException ignored) {
    }
  }

  /** DB가 참조하지 않는 오래된 임시/최종 파일만 root 내부에서 정리한다. */
  public void cleanupOrphans(Set<String> retained) {
    cleanupTree(root.resolve("staging"), retained);
    cleanupTree(root.resolve("attachments"), retained);
  }

  private void cleanupTree(Path directory, Set<String> retained) {
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))
      return;
    Instant cutoff = Instant.now().minus(Duration.ofHours(1));
    try (Stream<Path> paths = Files.walk(directory)) {
      paths
          .filter(
              p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(p))
          .forEach(
              path -> {
                try {
                  String relative = root.relativize(safe(path)).toString().replace('\\', '/');
                  boolean temporary = relative.endsWith(".tmp");
                  if ((temporary || !retained.contains(relative))
                      && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
                          .toInstant()
                          .isBefore(cutoff)) Files.deleteIfExists(path);
                } catch (IOException | BusinessException ignored) {
                }
              });
    } catch (IOException ignored) {
    }
  }

  private Path safe(Path path) {
    Path n = path.toAbsolutePath().normalize();
    if (!n.startsWith(root)) throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
    return n;
  }

  private Path real(Path path) throws IOException {
    Path r = path.toRealPath();
    if (!r.startsWith(root)) throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
    return r;
  }

  public record Stored(String storedName, String relativePath) {}
}
