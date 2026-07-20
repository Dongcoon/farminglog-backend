package com.farmlog.report;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMapperContractTest {
    @Test
    void allAggregationsAreTenantScopedSoftDeleteAwareAndUnitAware() throws Exception {
        String xml;
        try (var stream = getClass().getResourceAsStream("/mapper/report/ReportMapper.xml")) {
            assertThat(stream).isNotNull();
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(xml).contains("farm_id=#{farmId}", "deleted_at IS NULL", "LOWER(TRIM(unit))",
                "findHarvestByZone", "findHarvestByVariety", "findSalesByCustomer",
                "harvest_date&gt;=#{start}", "sales_date&gt;=#{start}");
        // 구역·품종은 수확에서, 거래처는 판매에서만 산출하여 서로 다른 도메인을 추론하지 않는다.
        assertThat(xml.substring(xml.indexOf("findHarvestByZone"), xml.indexOf("findHarvestByVariety")))
                .contains("harvest_log").doesNotContain("sales_log");
        assertThat(xml.substring(xml.indexOf("findSalesByCustomer"), xml.indexOf("countOwnerCapabilities")))
                .contains("sales_log").doesNotContain("harvest_log");
        assertThat(xml).doesNotContain("SUM(l.quantity) +");
    }

    @Test
    void aggregateUsesFixedOwnerFarmSetAndBulkQueries() throws Exception {
      String xml;
      try (var stream = getClass().getResourceAsStream("/mapper/report/ReportMapper.xml")) {
        assertThat(stream).isNotNull();
        xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }

      assertThat(xml)
          .contains(
              "countOwnerCapabilities",
              "f.owner_user_id=#{userId}",
              "fm.role='FARM_OWNER'",
              "fm.status='ACTIVE'",
              "f.status='ACTIVE'",
              "f.lifecycle_status='ACTIVE'",
              "f.deleted_at IS NULL",
              "<foreach collection=\"farmIds\"",
              "findAggregateRecordCounts",
              "findAggregateHarvestComparison",
              "findAggregateSalesComparison",
              "findAggregateAverageUnitPrices",
              "LOWER(TRIM(unit))");
    }
}
