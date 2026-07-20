package com.farmlog.farmstructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import com.farmlog.farmstructure.dto.StructureDtos.Conflict;
import com.farmlog.farmstructure.dto.StructureDtos.Warning;

/** 저장 JSON과 SHA-256 입력이 JVM/한글/Map 순서에 따라 달라지지 않게 하는 전용 serializer. */
@Component
public class StructureCanonicalJson {
  private final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public String write(Object value) {
    try { return mapper.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
  }

  public <T> T read(String json, Class<T> type) {
    try { return mapper.readValue(json, type); }
    catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
  }

  public Object readObject(String json) { return read(json, Object.class); }
  public List<Conflict> readConflicts(String json) {
    try { return mapper.readValue(json, new TypeReference<>() {}); }
    catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
  }
  public List<Warning> readWarnings(String json) {
    try { return mapper.readValue(json, new TypeReference<>() {}); }
    catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
  }

  public String hash(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(write(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
