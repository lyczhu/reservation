package com.lawyus.reservation.common.audit;

import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.audit.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * 审计日志写入：敏感操作（规则变更、查看完整手机号、账号管理等）在业务代码内显式调用，不依赖切面。
 */
@Service
public class AuditService {

    public static final int OPERATOR_USER = 1;
    public static final int OPERATOR_STAFF = 2;

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 仅在值变化时追加一段「字段 旧值->新值」，供业务层拼审计详情。 */
    public static void appendChange(StringBuilder detail, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        if (detail.length() > 0) {
            detail.append("; ");
        }
        detail.append(field).append(' ').append(text(before)).append("->").append(text(after));
    }

    private static String text(Object value) {
        return value == null ? "空" : String.valueOf(value);
    }

    public void record(int operatorType, long operatorId, String action, String target, String detail) {
        AuditLog log = new AuditLog();
        log.setStoreId(AppConstants.STORE_ID);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail);
        log.setIp(currentIp());
        auditLogMapper.insert(log);
    }

    private String currentIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
