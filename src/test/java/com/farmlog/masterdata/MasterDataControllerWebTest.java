package com.farmlog.masterdata;

import com.farmlog.common.security.*;
import com.farmlog.masterdata.dto.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MasterDataController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtTokenProvider.class})
class MasterDataControllerWebTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockBean MasterDataService service;

    private String token() { return tokens.createAccessToken(42L, "farmer@example.com"); }

    @Test
    void everyMasterDataEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/master-data/templates/STRAWBERRY"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
        mvc.perform(get("/farms/1/crops"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cropListDefaultsToActiveOnlyAndCreateReturns201() throws Exception {
        when(service.listCrops(42L, 1L, false)).thenReturn(List.of(new CatalogResponse(3L, "딸기", 0, true)));
        mvc.perform(get("/farms/1/crops").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].activeYn").value(true));
        verify(service).listCrops(42L, 1L, false);

        when(service.createCrop(eq(42L), eq(1L), any())).thenReturn(new CatalogResponse(3L, "딸기", 0, true));
        mvc.perform(post("/farms/1/crops").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"딸기\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("딸기"));
    }

    @Test
    void deactivateAndCompleteReturnUpdatedBodyWith200() throws Exception {
        when(service.deactivateWorkType(42L, 1L, 9L)).thenReturn(new WorkTypeResponse(9L, "관수", 0, false));
        mvc.perform(patch("/farms/1/work-types/9/deactivate").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeYn").value(false));

        when(service.completeCropSeason(42L, 1L, 5L)).thenReturn(
                new CropSeasonResponse(5L, 3L, null, "2026 작기", null, null, "COMPLETED"));
        mvc.perform(patch("/farms/1/crop-seasons/5/complete").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void nullablePatchJsonPreservesExplicitNullPresence() throws Exception {
        when(service.updateCustomer(eq(42L), eq(1L), eq(8L), any())).thenReturn(
                new CustomerResponse(8L, "공판장", "OTHER", null, null, true));
        mvc.perform(patch("/farms/1/customers/8").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":null,\"memo\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").doesNotExist());
        ArgumentCaptor<CustomerUpdateRequest> captor = ArgumentCaptor.forClass(CustomerUpdateRequest.class);
        verify(service).updateCustomer(eq(42L), eq(1L), eq(8L), captor.capture());
        assertThat(captor.getValue().hasPhone()).isTrue();
        assertThat(captor.getValue().hasMemo()).isTrue();
    }

    @Test
    void templatePreviewAndApplyExposeStableCountsShape() throws Exception {
        when(service.previewStrawberryTemplate()).thenReturn(new TemplatePreviewResponse("STRAWBERRY",
                new TemplatePreviewResponse.CropTemplate("딸기", List.of("설향")), List.of("정식")));
        mvc.perform(get("/master-data/templates/STRAWBERRY").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.crop.name").value("딸기"));

        when(service.applyStrawberryTemplate(42L, 1L)).thenReturn(new TemplateApplyResponse("STRAWBERRY",
                new TemplateApplyResponse.Counts(1, 3, 5), new TemplateApplyResponse.Counts(0, 0, 0)));
        mvc.perform(post("/farms/1/master-data/templates/STRAWBERRY/apply")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created.varieties").value(3))
                .andExpect(jsonPath("$.reused.workTypes").value(0));
    }

    @Test
    void invalidCreatePayloadReturns400() throws Exception {
        mvc.perform(post("/farms/1/materials").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void cropSeasonDeleteIsSoftDeleteServiceContractAndReturns204() throws Exception {
        mvc.perform(delete("/farms/1/crop-seasons/5").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());
        verify(service).deleteCropSeason(42L, 1L, 5L);
    }
}
