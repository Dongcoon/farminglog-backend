package com.farmlog.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentStorageTest {
  @TempDir Path directory;

  @Test
  void partialFallbackMoveFailureImmediatelyDeletesTempAndTarget() {
    class FailingStorage extends AttachmentStorage {
      Path temp;
      Path target;

      FailingStorage(Path root) {
        super(root.toString());
      }

      @Override
      void moveIntoPlace(Path temp, Path target) throws IOException {
        this.temp = temp;
        this.target = target;
        // 비원자 move가 target을 만든 직후 실패한 드문 파일시스템 상황을 재현한다.
        Files.copy(temp, target);
        throw new IOException("simulated partial fallback move");
      }
    }

    FailingStorage storage = new FailingStorage(directory);
    assertThatThrownBy(() -> storage.writeFinal(11L, "jpg", new byte[] {1, 2, 3}))
        .isInstanceOf(IOException.class);
    assertThat(storage.temp).doesNotExist();
    assertThat(storage.target).doesNotExist();
  }
}
