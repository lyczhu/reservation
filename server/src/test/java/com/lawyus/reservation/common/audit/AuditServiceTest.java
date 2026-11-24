package com.lawyus.reservation.common.audit;

import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.audit.mapper.AuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** P1：审计写入逻辑测试（appendChange 变更拼接 + record 落库字段）。 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    AuditLogMapper auditLogMapper;

    @InjectMocks
    AuditService auditService;

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void appendChange_whenValueChanged_appendsFieldBeforeAfter() {
        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "priceCents", 19800, 22800);
        assertThat(detail.toString()).isEqualTo("priceCents 19800->22800");
    }

    @Test
    void appendChange_whenValueUnchanged_appendsNothing() {
        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "name", " same ", " same ");
        assertThat(detail).isEmpty();
    }

    @Test
    void appendChange_whenValueIsNull_rendersAsChineseEmpty() {
        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "logo", null, "https://x/y.png");
        AuditService.appendChange(detail, "announcement", "旧公告", null);
        assertThat(detail.toString()).isEqualTo("logo 空->https://x/y.png; announcement 旧公告->空");
    }

    @Test
    void appendChange_multipleChanges_joinedWithSemicolon() {
        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "name", "旧名", "新名");
        AuditService.appendChange(detail, "priceCents", 100, 200);
        AuditService.appendChange(detail, "status", 1, 1);
        assertThat(detail.toString()).isEqualTo("name 旧名->新名; priceCents 100->200");
    }

    @Test
    void record_persistsAllAuditFields() {
        auditService.record(AuditService.OPERATOR_STAFF, 99L, "SERVICE_UPDATE", "service:12", "priceCents 100->200");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(AppConstants.STORE_ID);
        assertThat(saved.getOperatorType()).isEqualTo(AuditService.OPERATOR_STAFF);
        assertThat(saved.getOperatorId()).isEqualTo(99L);
        assertThat(saved.getAction()).isEqualTo("SERVICE_UPDATE");
        assertThat(saved.getTarget()).isEqualTo("service:12");
        assertThat(saved.getDetail()).isEqualTo("priceCents 100->200");
    }

    @Test
    void record_withForwardedFor_takesFirstIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.record(AuditService.OPERATOR_USER, 7L, "VIEW_PHONE", "reservation:R1", "查看手机号");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("203.0.113.9");
    }
}
