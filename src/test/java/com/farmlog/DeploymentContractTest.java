package com.farmlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** 실행 문서와 실제 Compose/env 계약이 다시 어긋나는 것을 막는 정적 배포 회귀 테스트. */
class DeploymentContractTest {

  @Test
  @SuppressWarnings("unchecked")
  void composeIsValidYamlAndKeepsRuntimeArtifactsInNamedVolumes() throws IOException {
    Map<String, Object> compose = new Yaml().load(read("docker-compose.yml"));
    Map<String, Object> services = (Map<String, Object>) compose.get("services");
    Map<String, Object> volumes = (Map<String, Object>) compose.get("volumes");

    assertThat(services.keySet()).contains(
        "farmlog-api", "farmlog-web", "farmlog-nginx", "farmlog-mariadb");
    assertThat(volumes.keySet()).contains(
        "farmlog-uploads", "farmlog-exports", "farmlog-db-data");
    assertThat(services.toString())
        .contains("local-db", "service_healthy", "/app/uploads", "/app/exports");
  }

  @Test
  void environmentExampleCoversEveryExportSettingAndDoesNotPromiseAutomaticSeed()
      throws IOException {
    String env = read(".env.example");
    String application = read("src/main/resources/application.yml");

    assertThat(env).contains(
        "EXPORT_ROOT_DIR=", "EXPORT_RETENTION_DAYS=", "EXPORT_XLSX_MAX_ROWS=",
        "EXPORT_PDF_MAX_ROWS=", "EXPORT_LEASE_MINUTES=", "EXPORT_MAX_ATTEMPTS=",
        "EXPORT_POLL_DELAY_MS=");
    assertThat(env + application).doesNotContain("SEED_ENABLED", "farmlog.seed");
  }

  private static String read(String relativePath) throws IOException {
    return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
  }
}
