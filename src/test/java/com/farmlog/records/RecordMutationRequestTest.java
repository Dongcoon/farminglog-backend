package com.farmlog.records;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.farmlog.records.dto.RecordMutationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordMutationRequestTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void patchDistinguishesMissingFromExplicitNullForNullableFields() throws Exception {
        RecordMutationRequest missing = mapper.readValue("{}", RecordMutationRequest.class);
        RecordMutationRequest clear = mapper.readValue(
                "{\"memo\":null,\"amountValue\":null,\"amountUnit\":null,\"netAmountOverride\":null}",
                RecordMutationRequest.class);

        assertThat(missing.presentFields()).isEmpty();
        assertThat(clear.presentFields()).containsExactlyInAnyOrder(
                "memo", "amountValue", "amountUnit", "netAmountOverride");
        assertThat(clear.memo()).isNull();
        assertThat(clear.netAmountOverride()).isNull();
    }

    @Test
    void koreanTextAndDatesRoundTripWithoutDamage() throws Exception {
        RecordMutationRequest request = mapper.readValue(
                "{\"workDate\":\"2026-07-01\",\"memo\":\"딸기 하우스 관수 완료\"}",
                RecordMutationRequest.class);
        assertThat(request.memo()).isEqualTo("딸기 하우스 관수 완료");
        assertThat(request.workDate()).hasToString("2026-07-01");
    }
}
