package com.farmlog.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlog.masterdata.dto.CropSeasonUpdateRequest;
import com.farmlog.masterdata.dto.CustomerUpdateRequest;
import com.farmlog.masterdata.dto.MaterialUpdateRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullablePatchRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cropSeason_distinguishesMissingFromExplicitNull() throws Exception {
        CropSeasonUpdateRequest missing = mapper.readValue("{}", CropSeasonUpdateRequest.class);
        CropSeasonUpdateRequest clear = mapper.readValue("{\"varietyId\":null,\"endDate\":null}", CropSeasonUpdateRequest.class);
        assertThat(missing.hasVarietyId()).isFalse();
        assertThat(missing.hasEndDate()).isFalse();
        assertThat(clear.hasVarietyId()).isTrue();
        assertThat(clear.hasEndDate()).isTrue();
    }

    @Test
    void customerAndMaterial_distinguishNullableFields() throws Exception {
        CustomerUpdateRequest customer = mapper.readValue("{\"phone\":null,\"memo\":null}", CustomerUpdateRequest.class);
        MaterialUpdateRequest material = mapper.readValue("{\"unit\":null,\"memo\":null}", MaterialUpdateRequest.class);
        assertThat(customer.hasPhone()).isTrue();
        assertThat(customer.hasMemo()).isTrue();
        assertThat(material.hasUnit()).isTrue();
        assertThat(material.hasMemo()).isTrue();
    }
}
