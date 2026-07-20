package com.farmlog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farmlog.admin.entity.AdminAuditRow;
import com.farmlog.admin.entity.AdminUserRow;
import com.farmlog.admin.mapper.AdminMapper;
import com.farmlog.common.exception.BusinessException;
import com.farmlog.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminServiceTest {
  private AdminMapper mapper;
  private AdminAccessGuard guard;
  private AdminService service;

  @BeforeEach
  void setUp() {
    mapper = mock(AdminMapper.class);
    guard = mock(AdminAccessGuard.class);
    service = new AdminService(mapper, guard, new ObjectMapper().findAndRegisterModules());
  }

  @Test
  void usersMasksEmailAndWritesExactlyOneAuditAfterSnapshotWithoutRawQuery() {
    AdminUserRow row = new AdminUserRow();
    row.setId(9L);
    row.setEmail("owner@example.com");
    row.setDisplayName("농장주");
    row.setStatus("ACTIVE");
    when(mapper.findUsers(
            anyString(), isNull(), isNull(), anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of(row));
    when(mapper.countUsers(anyString(), isNull(), isNull())).thenReturn(1L);
    ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);

    var result =
        service.users(
            7L,
            new AdminFilters.Users("private@example.com", null, null, 0, 20, "createdAt,desc"),
            new AdminFilters.RequestMeta("192.168.1.25", "테스트 브라우저"));

    assertThat(result.content())
        .singleElement()
        .satisfies(user -> assertThat(user.maskedEmail()).isEqualTo("o***@example.com"));
    var order = inOrder(mapper);
    order
        .verify(mapper)
        .findUsers(anyString(), isNull(), isNull(), eq("u.created_at"), eq("DESC"), eq(0), eq(20));
    order.verify(mapper).countUsers(anyString(), isNull(), isNull());
    order
        .verify(mapper)
        .insertViewAudit(eq(7L), eq("ADMIN_USERS_VIEW"), any(), any(), detail.capture(), any());
    verify(mapper, times(1))
        .insertViewAudit(anyLong(), anyString(), any(), any(), anyString(), any());
    assertThat(detail.getValue())
        .contains("\"qPresent\":true")
        .doesNotContain("private@example.com");
  }

  @Test
  void auditLogsRecursivelyMaskSensitiveDetailAndIp() {
    AdminAuditRow row = new AdminAuditRow();
    row.setId(3L);
    row.setActorUserId(9L);
    row.setActorName("관리자");
    row.setActorEmail("admin@example.com");
    row.setIpAddress("10.20.30.40");
    row.setAction("EXPORT_CREATE");
    row.setTargetType("EXPORT_JOB");
    row.setDetailJson(
        """
{"email":"person@example.com","nested":{"accessToken":"secret-value","path":"C:\\\\private\\\\file"},"memo":"민감 메모","ipAddress":"172.16.0.10"}
""");
    when(mapper.findAuditLogs(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            anyString(),
            anyString(),
            anyInt(),
            anyInt()))
        .thenReturn(List.of(row));
    when(mapper.countAuditLogs(
            isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
        .thenReturn(1L);

    var result =
        service.auditLogs(
            7L,
            new AdminFilters.Audits(null, null, null, null, null, null, null, null, 0, 20, null),
            new AdminFilters.RequestMeta("127.0.0.1", "agent"));

    var audit = result.content().get(0);
    assertThat(audit.ipAddressMasked()).isEqualTo("10.20.30.***");
    assertThat(audit.actor().maskedEmail()).isEqualTo("a***@example.com");
    assertThat(audit.detail().toString())
        .contains("p***@example.com", "[REDACTED]", "172.16.0.***")
        .doesNotContain("secret-value", "민감 메모", "private\\file");
    verify(mapper, times(1))
        .insertViewAudit(anyLong(), eq("ADMIN_AUDIT_LOGS_VIEW"), any(), any(), anyString(), any());
  }

  @Test
  void invalidSortFailsBeforeQueryOrAudit() {
    assertThatThrownBy(
            () ->
                service.users(
                    7L,
                    new AdminFilters.Users(null, null, null, 0, 20, "email,asc"),
                    new AdminFilters.RequestMeta(null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    verify(mapper, never()).findUsers(any(), any(), any(), any(), any(), anyInt(), anyInt());
    verify(mapper, never()).insertViewAudit(any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectedGuardDoesNotTouchAdminData() {
    BusinessException forbidden = new BusinessException(ErrorCode.FORBIDDEN);
    org.mockito.Mockito.doThrow(forbidden).when(guard).requireSystemAdmin(7L);

    assertThatThrownBy(
            () ->
                service.users(
                    7L,
                    new AdminFilters.Users(null, null, null, 0, 20, null),
                    new AdminFilters.RequestMeta(null, null)))
        .isSameAs(forbidden);

    verify(mapper, never()).findUsers(any(), any(), any(), any(), any(), anyInt(), anyInt());
  }

  @Test
  void abbreviatedIpv6NeverExposesTheFullAddress() {
    assertThat(AdminService.maskIp("2001:db8::1234"))
        .isEqualTo("2001:db8::****")
        .doesNotContain("1234");
    assertThat(AdminService.maskIp("::1")).isEqualTo("1::****");
  }
}
