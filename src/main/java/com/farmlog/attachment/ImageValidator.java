package com.farmlog.attachment;

import com.farmlog.common.exception.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 확장자·선언 MIME·magic bytes·실제 decode를 모두 통과한 이미지만 저장한다. */
@Component
public class ImageValidator {
  public static final long MAX_BYTES = 5L * 1024 * 1024, MAX_PIXELS = 25_000_000L;
  private static final Map<String, String> MIME =
      Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

  public ImageInspection inspect(MultipartFile file) {
    try {
      if (file == null || file.isEmpty()) throw invalid("빈 파일은 업로드할 수 없습니다.");
      if (file.getSize() > MAX_BYTES)
        throw new BusinessException(ErrorCode.ATTACHMENT_TOO_LARGE, "사진은 5 MiB 이하여야 합니다.");
      String name = Optional.ofNullable(file.getOriginalFilename()).orElse("");
      int dot = name.lastIndexOf('.');
      String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
      String expected = MIME.get(ext);
      if (expected == null) throw new BusinessException(ErrorCode.ATTACHMENT_INVALID_TYPE);
      byte[] bytes = file.getBytes();
      String actual = magic(bytes);
      if (!expected.equals(actual)
          || !expected.equalsIgnoreCase(Optional.ofNullable(file.getContentType()).orElse("")))
        throw invalid("파일 확장자, MIME, 실제 형식이 일치하지 않습니다.");
      try (ImageInputStream input =
          ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        if (input == null) throw invalid("이미지를 실제로 해석할 수 없습니다.");
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw invalid("이미지를 실제로 해석할 수 없습니다.");
        ImageReader reader = readers.next();
        try {
          reader.setInput(input, true, true);
          // 압축 폭탄 방어: 픽셀 버퍼를 할당하기 전에 헤더의 크기를 먼저 제한한다.
          int width = reader.getWidth(0), height = reader.getHeight(0);
          long pixels = (long) width * height;
          if (pixels <= 0 || pixels > MAX_PIXELS) throw invalid("사진 해상도는 25MP 이하여야 합니다.");
          if (reader.read(0) == null) throw invalid("이미지를 실제로 해석할 수 없습니다.");
          return new ImageInspection(
              "jpeg".equals(ext) ? "jpg" : ext, actual, width, height, bytes);
        } finally {
          reader.dispose();
        }
      }
    } catch (BusinessException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw invalid("사진 파일을 읽을 수 없습니다.");
    }
  }

  private String magic(byte[] b) {
    if (b.length >= 3 && (b[0] & 255) == 0xff && (b[1] & 255) == 0xd8 && (b[2] & 255) == 0xff)
      return "image/jpeg";
    if (b.length >= 8
        && (b[0] & 255) == 0x89
        && b[1] == 'P'
        && b[2] == 'N'
        && b[3] == 'G'
        && (b[4] & 255) == 0x0d
        && (b[5] & 255) == 0x0a
        && (b[6] & 255) == 0x1a
        && (b[7] & 255) == 0x0a) return "image/png";
    if (b.length >= 12
        && new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
        && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))
      return "image/webp";
    return "";
  }

  private BusinessException invalid(String m) {
    return new BusinessException(ErrorCode.ATTACHMENT_INVALID_TYPE, m);
  }
}
