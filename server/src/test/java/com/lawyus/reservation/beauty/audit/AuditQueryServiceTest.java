package com.lawyus.reservation.beauty.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.reservation.beauty.account.StaffAccount;
import com.lawyus.reservation.beauty.account.mapper.StaffAccountMapper;
import com.lawyus.reservation.beauty.audit.dto.AuditActionVO;
import com.lawyus.reservation.beauty.audit.dto.AuditLogVO;
import com.lawyus.reservation.beauty.audit.dto.AuditQueryResult;
import com.lawyus.reservation.beauty.category.mapper.ServiceCategoryMapper;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditLog;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.audit.mapper.AuditLogMapper;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.customer.account.User;
import com.lawyus.reservation.customer.account.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 审计查询逻辑测试：参数校验、分页钳制、动作映射、姓名关联与可读详情加工。 */
@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock AuditLogMapper auditLogMapper;
    @Mock StaffAccountMapper staffAccountMapper;
    @Mock UserMapper userMapper;
    @Mock ServiceItemMapper serviceItemMapper;
    @Mock ServiceCategoryMapper serviceCategoryMapper;
    @Mock TechnicianMapper technicianMapper;

    @InjectMocks AuditQueryService service;

    private static AuditLog log(long id, int operatorType, long operatorId, String action) {
        return log(id, operatorType, operatorId, action, "detail-" + id);
    }

    private static AuditLog log(long id, int operatorType, long operatorId, String action, String detail) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setStoreId(1L);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTarget("service:" + id);
        log.setDetail(detail);
        log.setIp("0:0:0:0:0:0:0:1");
        log.setCreatedAt(LocalDateTime.now().withNano(0));
        return log;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Page<AuditLog> pageOf(List<AuditLog> records, long total) {
        Page<AuditLog> page = new Page<>(1, 20);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    private void stubQuery(List<AuditLog> records, long total) {
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageOf(records, total));
        when(auditLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);
    }

    // ---------- 参数校验 ----------

    @Test
    void query_operatorTypeInvalid_throwsParamInvalid() {
        assertThatThrownBy(() -> service.query(null, null, 3, null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> service.query(null, null, 0, null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void query_categoryInvalid_throwsParamInvalid() {
        assertThatThrownBy(() -> service.query(null, "不存在的分类", null, null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void query_dateRangeReversed_throwsParamInvalid() {
        assertThatThrownBy(() -> service.query(null, null, null, null,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 19), null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void query_actionOrKeywordTooLong_throwsParamInvalid() {
        String tooLong = "x".repeat(51);
        assertThatThrownBy(() -> service.query(tooLong, null, null, null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query(null, null, null, null, null, null, tooLong, 1, 20))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 分页钳制 ----------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void query_pageAndSizeClamped_toValidRange() {
        stubQuery(List.of(), 0);

        service.query(null, null, null, null, null, null, null, -5, 500);

        ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
        verify(auditLogMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(100);
    }

    // ---------- 动作目录与中文映射 ----------

    @Test
    void actions_exposeActionValuesAndCategories() {
        List<AuditActionVO> options = service.actions();

        assertThat(options).hasSize(24);
        assertThat(options).anySatisfy(option -> {
            assertThat(option.value()).isEqualTo("RULES_UPDATE");
            assertThat(option.text()).isEqualTo("修改预约规则");
            assertThat(option.category()).isEqualTo(AuditCatalog.CATEGORY_RULE);
            assertThat(option.riskLevel()).isEqualTo(AuditCatalog.RISK_MEDIUM);
        });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void query_mapsKnownActionsToChineseText() {
        String[] actions = {
                "VIEW_PHONE", "VERIFY_LOCK", "RULES_UPDATE", "ACCOUNT_CREATE", "ACCOUNT_STATUS",
                "PASSWORD_RESET", "WX_BIND", "WX_UNBIND", "SERVICE_CREATE", "SERVICE_UPDATE",
                "SERVICE_STATUS", "SERVICE_DELETE", "SERVICE_SORT", "CATEGORY_CREATE", "CATEGORY_UPDATE",
                "CATEGORY_STATUS", "CATEGORY_DELETE", "CATEGORY_SORT", "TECHNICIAN_CREATE", "TECHNICIAN_UPDATE",
                "TECHNICIAN_STATUS", "SHIFT_UPDATE", "SHIFT_COPY", "STORE_UPDATE"
        };
        List<AuditLog> logs = new java.util.ArrayList<>();
        for (int i = 0; i < actions.length; i++) {
            logs.add(log(i + 1, AuditService.OPERATOR_STAFF, 99L, actions[i]));
        }
        stubQuery(logs, logs.size());

        AuditQueryResult result = service.query(null, null, null, null, null, null, null, 1, 20);

        assertThat(result.records()).hasSize(24);
        assertThat(result.records().stream().map(AuditLogVO::actionText).toList())
                .containsExactly("查看完整手机号", "核销锁定", "修改预约规则", "创建账号", "账号启停",
                        "重置密码", "绑定微信", "解绑微信", "创建服务", "编辑服务",
                        "服务上下架", "删除服务", "服务排序", "创建分类", "编辑分类",
                        "分类启停", "删除分类", "分类排序", "创建技师", "编辑技师",
                        "技师启停", "排班变更", "排班复制", "门店资料变更");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void query_unknownAction_fallsBackToRawValue() {
        stubQuery(List.of(log(1, AuditService.OPERATOR_STAFF, 99L, "CUSTOM_ACTION")), 1);

        AuditQueryResult result = service.query(null, null, null, null, null, null, null, 1, 20);

        assertThat(result.records().get(0).actionText()).isEqualTo("CUSTOM_ACTION");
    }

    // ---------- 可读详情与风险 ----------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void query_rulesUpdate_buildsChangeListAndSummary() {
        AuditLog rulesLog = log(1, AuditService.OPERATOR_STAFF, 5L, "RULES_UPDATE",
                "slotGranularity 30->60; bookableDays 14->7; minLeadMinutes 60->120; "
                        + "cancelDeadlineHours 2->3; rescheduleDeadlineHours 2->3; needConfirm 1->0; "
                        + "bufferMinutes 0->10; reminderHours 2->3");
        rulesLog.setTarget("rule_config");
        stubQuery(List.of(rulesLog), 1);

        AuditQueryResult result = service.query(null, null, null, null, null, null, null, 1, 20);
        AuditLogVO vo = result.records().get(0);

        assertThat(vo.targetText()).isEqualTo("预约规则");
        assertThat(vo.category()).isEqualTo(AuditCatalog.CATEGORY_RULE);
        assertThat(vo.riskText()).isEqualTo("中风险");
        assertThat(vo.changes()).hasSize(8);
        assertThat(vo.changes().get(0).label()).isEqualTo("时段粒度");
        assertThat(vo.changes().get(0).before()).isEqualTo("30 分钟");
        assertThat(vo.changes().get(0).after()).isEqualTo("60 分钟");
        assertThat(vo.summary()).contains("时段粒度 30 分钟 → 60 分钟", "等 8 项变更");
        assertThat(result.summary().highRisk()).isZero();
        assertThat(result.summary().today()).isEqualTo(1);
        assertThat(vo.ipText()).isEqualTo("本机 · 0:0:0:0:0:0:0:1");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void query_viewPhone_isHighRisk() {
        AuditLog phoneLog = log(1, AuditService.OPERATOR_STAFF, 5L, "VIEW_PHONE", "查看预约 1001 的完整手机号");
        phoneLog.setTarget("reservation");
        stubQuery(List.of(phoneLog), 1);

        AuditQueryResult result = service.query(null, null, null, null, null, null, null, 1, 20);

        assertThat(result.records().get(0).riskLevel()).isEqualTo(AuditCatalog.RISK_HIGH);
        assertThat(result.records().get(0).targetText()).isEqualTo("预约单");
        assertThat(result.summary().highRisk()).isEqualTo(1);
    }

    // ---------- 操作人姓名与类型关联 ----------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void query_resolvesOperatorNameByType() {
        stubQuery(List.of(
                log(1, AuditService.OPERATOR_STAFF, 5L, "SERVICE_UPDATE"),
                log(2, AuditService.OPERATOR_USER, 8L, "VIEW_PHONE")
        ), 2);

        StaffAccount staff = new StaffAccount();
        staff.setId(5L);
        staff.setUsername("13800000000");
        when(staffAccountMapper.selectByIds(any())).thenReturn(List.of(staff));

        User user = new User();
        user.setId(8L);
        user.setNickname("顾客小李");
        when(userMapper.selectByIds(any())).thenReturn(List.of(user));

        AuditQueryResult result = service.query(null, null, null, null, null, null, null, 1, 20);

        assertThat(result.records()).hasSize(2);
        AuditLogVO staffRow = result.records().get(0);
        assertThat(staffRow.operatorTypeText()).isEqualTo("商家账号");
        assertThat(staffRow.operatorName()).isEqualTo("13800000000");
        AuditLogVO userRow = result.records().get(1);
        assertThat(userRow.operatorTypeText()).isEqualTo("顾客");
        assertThat(userRow.operatorName()).isEqualTo("顾客小李");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void query_emptyResult_returnsEmptyPage() {
        stubQuery(List.of(), 0);

        AuditQueryResult result = service.query(null, null, null, null, null, null, null, 1, 20);

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.summary().total()).isZero();
    }
}
