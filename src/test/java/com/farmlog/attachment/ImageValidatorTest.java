package com.farmlog.attachment;

import static org.assertj.core.api.Assertions.*;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageValidatorTest {
  private final ImageValidator validator = new ImageValidator();

  @Test
  void acceptsDecodedJpegAndPng() throws Exception {
    assertThat(validator.inspect(file("a.jpg", "image/jpeg", image("jpg"))).contentType())
        .isEqualTo("image/jpeg");
    assertThat(validator.inspect(file("가.png", "image/png", image("png"))).contentType())
        .isEqualTo("image/png");
  }

  @Test
  void acceptsDecodedWebpWhenPluginIsInstalled() throws Exception {
    byte[] bytes = image("webp");
    assertThat(bytes).isNotEmpty();
    assertThat(validator.inspect(file("a.webp", "image/webp", bytes)).contentType())
        .isEqualTo("image/webp");
  }

  @Test
  void rejectsMimeSpoofAndOversizedHeaderBeforeDecode() throws Exception {
    assertThatThrownBy(() -> validator.inspect(file("a.png", "image/png", image("jpg"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ATTACHMENT_INVALID_TYPE));
    byte[] bomb = image("png");
    writeInt(bomb, 16, 5001);
    writeInt(bomb, 20, 5000);
    CRC32 crc = new CRC32();
    crc.update(bomb, 12, 17);
    writeInt(bomb, 29, (int) crc.getValue());
    assertThatThrownBy(() -> validator.inspect(file("bomb.png", "image/png", bomb)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ATTACHMENT_INVALID_TYPE));
  }

  private MockMultipartFile file(String name, String mime, byte[] bytes) {
    return new MockMultipartFile("file", name, mime, bytes);
  }

  private byte[] image(String format) throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertThat(ImageIO.write(image, format, out)).isTrue();
    return out.toByteArray();
  }

  private void writeInt(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) (value >>> 24);
    bytes[offset + 1] = (byte) (value >>> 16);
    bytes[offset + 2] = (byte) (value >>> 8);
    bytes[offset + 3] = (byte) value;
  }
}
