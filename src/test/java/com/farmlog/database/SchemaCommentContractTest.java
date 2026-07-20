package com.farmlog.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SchemaCommentContractTest {
  private static final Path SCHEMA=Path.of("database/schema.sql");
  private static final Set<String> NON_COLUMNS=Set.of("primary","unique","key","constraint","check");
  private static final Pattern HANGUL=Pattern.compile("[가-힣]");
  private static final Pattern COLUMN=Pattern.compile("^    ([a-z][a-z0-9_]*)\\s+.*,$");
  private static final Pattern COLUMN_COMMENT=Pattern.compile(" COMMENT '((?:''|[^'])*)',$");
  private static final Pattern TABLE_COMMENT=Pattern.compile(
      "^\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='((?:''|[^'])*)';$");

  @Test
  void everyTableAndPhysicalColumnHasOneKoreanComment() throws Exception {
    String table=null;int tables=0,columns=0,tableComments=0,columnComments=0;
    for(String line:Files.readAllLines(SCHEMA,StandardCharsets.UTF_8)) {
      var create=Pattern.compile("^CREATE TABLE IF NOT EXISTS ([a-zA-Z0-9_]+) \\($").matcher(line);
      if(create.matches()){table=create.group(1);tables++;continue;}
      if(table==null)continue;
      var tableEnd=TABLE_COMMENT.matcher(line);
      if(tableEnd.matches()){
        assertThat(HANGUL.matcher(tableEnd.group(1)).find()).as("table %s",table).isTrue();
        tableComments++;table=null;continue;
      }
      var column=COLUMN.matcher(line);
      if(!column.matches()||NON_COLUMNS.contains(column.group(1)))continue;
      columns++;
      var comment=COLUMN_COMMENT.matcher(line);
      assertThat(comment.find()).as("%s.%s",table,column.group(1)).isTrue();
      assertThat(HANGUL.matcher(comment.group(1)).find()).as("%s.%s Korean",table,column.group(1)).isTrue();
      columnComments++;
    }
    assertThat(tables).isEqualTo(34);assertThat(tableComments).isEqualTo(tables);
    assertThat(columns).isEqualTo(489);assertThat(columnComments).isEqualTo(columns);
  }

  @Test
  void removingOnlyCommentsRestoresTheReviewedDdlSignature() throws Exception {
    String schema=Files.readString(SCHEMA,StandardCharsets.UTF_8).replace("\r\n","\n")
        .replaceAll(" COMMENT '(?:''|[^'])*'(?=,)","")
        .replaceAll(" COMMENT='(?:''|[^'])*'(?=;)","")
        .replaceFirst("[\\r\\n]+\\z","");
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(schema.getBytes(StandardCharsets.UTF_8)));
    // COMMENT 도입 직전 schema.sql의 타입·DEFAULT·제약·인덱스 전체 서명이다.
    assertThat(hash).isEqualTo("0d31fb8cdc26be9d743db48a9d646d5cfc8de822f4e5d4410e4e3ff8f8995c20");
  }

  @Test
  void generatedColumnAndTableOptionsKeepMariaDbCommentOrder() throws Exception {
    String schema=Files.readString(SCHEMA,StandardCharsets.UTF_8);
    assertThat(schema).contains(
        "current_marker TINYINT AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) PERSISTENT COMMENT '현재 소속 행 고유성 표시자',")
        .doesNotContain("COMMENT '현재 소속 행 고유성 표시자' PERSISTENT")
        .contains(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='");
  }
}
