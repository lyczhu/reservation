package com.lawyus.reservation.beauty.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 预约规则（rule_config 单店一行），字段与商家端可配置的规则参数一致。
 */
@Getter
@Setter
@TableName("rule_config")
public class RuleConfig {

    @TableId(type = IdType.INPUT)
    private Long storeId;
    private Integer slotGranularity;
    private Integer bookableDays;
    private Integer minLeadMinutes;
    private Integer cancelDeadlineHours;
    private Integer rescheduleDeadlineHours;
    private Integer needConfirm;
    private Integer maxConcurrent;
    private Integer bufferMinutes;
    private Integer reminderHours;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
