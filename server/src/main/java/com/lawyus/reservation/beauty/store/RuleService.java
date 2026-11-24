package com.lawyus.reservation.beauty.store;

import com.lawyus.reservation.beauty.store.dto.RuleUpdateRequest;
import com.lawyus.reservation.beauty.store.mapper.RuleConfigMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.StpStaffUtil;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 预约规则读写：max_concurrent 在 V1.0 固定为 1、不提供修改入口；
 * 规则变更仅影响新预约（已有预约按 rule_snapshot 执行），变更写审计日志。
 */
@Service
public class RuleService {

    private final RuleConfigMapper ruleConfigMapper;
    private final AuditService auditService;

    public RuleService(RuleConfigMapper ruleConfigMapper, AuditService auditService) {
        this.ruleConfigMapper = ruleConfigMapper;
        this.auditService = auditService;
    }

    public RuleConfig get() {
        RuleConfig config = ruleConfigMapper.selectById(AppConstants.STORE_ID);
        if (config == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "规则未初始化");
        }
        return config;
    }

    public void update(RuleUpdateRequest request) {
        if (!Set.of(30, 60).contains(request.slotGranularity())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "slotGranularity 仅支持 30/60");
        }
        RuleConfig old = get();

        RuleConfig update = new RuleConfig();
        update.setStoreId(AppConstants.STORE_ID);
        update.setSlotGranularity(request.slotGranularity());
        update.setBookableDays(request.bookableDays());
        update.setMinLeadMinutes(request.minLeadMinutes());
        update.setCancelDeadlineHours(request.cancelDeadlineHours());
        update.setRescheduleDeadlineHours(request.rescheduleDeadlineHours());
        update.setNeedConfirm(request.needConfirm());
        update.setBufferMinutes(request.bufferMinutes());
        update.setReminderHours(request.reminderHours());
        ruleConfigMapper.updateById(update);

        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "RULES_UPDATE", "rule_config",
                "slotGranularity " + old.getSlotGranularity() + "->" + update.getSlotGranularity()
                        + "; bookableDays " + old.getBookableDays() + "->" + update.getBookableDays()
                        + "; minLeadMinutes " + old.getMinLeadMinutes() + "->" + update.getMinLeadMinutes()
                        + "; cancelDeadlineHours " + old.getCancelDeadlineHours() + "->" + update.getCancelDeadlineHours()
                        + "; rescheduleDeadlineHours " + old.getRescheduleDeadlineHours() + "->" + update.getRescheduleDeadlineHours()
                        + "; needConfirm " + old.getNeedConfirm() + "->" + update.getNeedConfirm()
                        + "; bufferMinutes " + old.getBufferMinutes() + "->" + update.getBufferMinutes()
                        + "; reminderHours " + old.getReminderHours() + "->" + update.getReminderHours());
    }
}
