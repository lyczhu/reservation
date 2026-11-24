package com.lawyus.reservation.kernel.reservation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约单。active_slot 是数据库生成列（活跃态=1），不映射到实体；
 * 时段占用与核销码的活跃唯一性依赖该列上的唯一索引。
 */
@Getter
@Setter
@TableName("reservation")
public class Reservation {

    public static final int STATUS_WAIT_CONFIRM = 1;
    public static final int STATUS_WAIT_SERVICE = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_CANCELLED = 4;
    public static final int STATUS_NO_SHOW = 5;

    public static final int SOURCE_SELF = 1;
    public static final int SOURCE_PROXY = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String no;
    private Long storeId;
    private Long userId;
    private Long serviceId;
    private Long technicianId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer status;
    private String contactName;
    private String contactPhone;
    private String contactPhoneHash;
    private String remark;
    private Integer source;
    private String verifyCode;
    private String idempotencyKey;
    private String ruleSnapshot;
    private LocalDateTime remindedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime updatedAt;
}
