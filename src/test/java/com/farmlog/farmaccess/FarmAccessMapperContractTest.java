package com.farmlog.farmaccess;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 농장 구성원·매니저 배정 MyBatis SQL의 핵심 컬럼 계약을 검증한다. */
class FarmAccessMapperContractTest {

    @Test
    void careAssignmentListsSortByPhysicalCreatedAtColumn() throws Exception {
        String xml;
        try (var stream = getClass().getResourceAsStream("/mapper/farmaccess/FarmAccessMapper.xml")) {
            assertThat(stream).isNotNull();
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("a.created_at assigned_at")
                .contains("ORDER BY a.created_at DESC,a.id DESC")
                .doesNotContain("ORDER BY a.assigned_at");
    }
}
