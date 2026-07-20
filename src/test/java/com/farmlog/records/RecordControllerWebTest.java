package com.farmlog.records;

import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.farmlog.common.security.JwtAccessDeniedHandler;
import com.farmlog.common.security.JwtAuthenticationEntryPoint;
import com.farmlog.common.security.JwtAuthenticationFilter;
import com.farmlog.common.security.JwtTokenProvider;
import com.farmlog.common.security.SecurityConfig;
import com.farmlog.records.dto.BulkRequest;
import com.farmlog.records.dto.BulkResponse;
import com.farmlog.records.dto.PageResponse;
import com.farmlog.records.dto.RecordMutationRequest;
import com.farmlog.records.dto.RecordResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecordController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, JwtTokenProvider.class})
class RecordControllerWebTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockBean RecordService service;

    private String token() {
        return tokens.createAccessToken(42L, "farmer@example.com");
    }

    @Test
    void everyRecordEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/farms/1/work-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/farms/1/records")).andExpect(status().isUnauthorized());
        mvc.perform(get("/farms/1/record-authors")).andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns201AndPreservesExplicitNullOverride() throws Exception {
        when(service.create(eq(42L), eq(1L), eq(RecordType.SALES), any())).thenReturn(response(RecordType.SALES));
        mvc.perform(post("/farms/1/sales-logs").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesDate\":\"2026-07-01\",\"customerId\":9,\"quantity\":2," +
                                "\"unit\":\"kg\",\"unitPrice\":1000,\"netAmountOverride\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordType").value("SALES"));

        ArgumentCaptor<RecordMutationRequest> captor = ArgumentCaptor.forClass(RecordMutationRequest.class);
        verify(service).create(eq(42L), eq(1L), eq(RecordType.SALES), captor.capture());
        assertThat(captor.getValue().has("netAmountOverride")).isTrue();
        assertThat(captor.getValue().netAmountOverride()).isNull();
    }

    @Test
    void detailSerializesUppercaseRecordTypeAndPermissionFlags() throws Exception {
        when(service.detail(42L, 1L, RecordType.WORK, 5L)).thenReturn(response(RecordType.WORK));
        mvc.perform(get("/farms/1/work-logs/5").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordType").value("WORK"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.createdBy.displayName").value("작성자"));
    }

    @Test
    void deleteRequiresVersionAndReturns204() throws Exception {
        mvc.perform(delete("/farms/1/harvest-logs/8").header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/farms/1/harvest-logs/8?version=3").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());
        verify(service).delete(42L, 1L, RecordType.HARVEST, 8L, 3L);
    }

    @Test
    void staticBulkRouteWinsOverDetailAndValidatesBody() throws Exception {
        when(service.bulk(eq(42L), eq(1L), eq(RecordType.WORK), any()))
                .thenReturn(BulkResponse.deleted(List.of(3L)));
        mvc.perform(patch("/farms/1/work-logs/bulk").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"DELETE\",\"items\":[{\"id\":3,\"version\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1))
                .andExpect(jsonPath("$.ids[0]").value(3));
        verify(service).bulk(eq(42L), eq(1L), eq(RecordType.WORK), any(BulkRequest.class));

        mvc.perform(patch("/farms/1/work-logs/bulk").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"DELETE\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFeedAndAuthorsExposeStableShapes() throws Exception {
        when(service.list(eq(42L), eq(1L), eq(RecordType.WORK), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20), isNull()))
                .thenReturn(PageResponse.of(List.of(response(RecordType.WORK)), 0, 20, 1));
        mvc.perform(get("/farms/1/work-logs").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        when(service.feed(eq(42L), eq(1L), any(), any(), any(), any(), eq(List.of(RecordType.WORK, RecordType.SALES)),
                eq(0), eq(20), isNull())).thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        mvc.perform(get("/farms/1/records?types=WORK,SALES").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());

        when(service.authors(42L, 1L)).thenReturn(List.of(new RecordResponse.Actor(2L, "과거 작업자")));
        mvc.perform(get("/farms/1/record-authors").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("과거 작업자"));
    }

    @Test
    void invalidSettlementStatusFromListIsReturnedAs400() throws Exception {
        when(service.list(eq(42L), eq(1L), eq(RecordType.SALES), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq("SETTLED"), eq(0), eq(20), isNull()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_FAILED, "정산 상태는 PENDING 또는 DONE입니다."));

        mvc.perform(get("/farms/1/sales-logs?settlementStatus=SETTLED")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private RecordResponse response(RecordType type) {
        LocalDate date = LocalDate.of(2026, 7, 1);
        return new RecordResponse(type, 5L, 1L,
                type == RecordType.WORK ? date : null,
                type == RecordType.PEST_CONTROL ? date : null,
                type == RecordType.HARVEST ? date : null,
                type == RecordType.SALES ? date : null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                false, null, null,
                new RecordResponse.Actor(42L, "작성자"), "FARM_OWNER", false, "NOT_REQUIRED",
                null, null, null, null, null, 0L, true, true);
    }
}
