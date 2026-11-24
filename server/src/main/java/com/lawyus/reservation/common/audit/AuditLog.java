package com.lawyus.reservation.common.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志实体（audit_log）：仅追加，不修改不删除。
 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private Integer operatorType;
    private Long operatorId;
    private String action;
    private String target;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
