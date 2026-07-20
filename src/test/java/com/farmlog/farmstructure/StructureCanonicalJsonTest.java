package com.farmlog.farmstructure;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class StructureCanonicalJsonTest {
  private final StructureCanonicalJson canonical = new StructureCanonicalJson();

  @Test
  void mapOrderAndKoreanUtf8ProduceStableHash() {
    Map<String,Object> first = new LinkedHashMap<>();
    first.put("한글", "농장 병합"); first.put("id", 3);
    Map<String,Object> second = new LinkedHashMap<>();
    second.put("id", 3); second.put("한글", "농장 병합");
    assertThat(canonical.write(first)).isEqualTo(canonical.write(second));
    assertThat(canonical.hash(first)).isEqualTo(canonical.hash(second)).hasSize(64);
  }
}
