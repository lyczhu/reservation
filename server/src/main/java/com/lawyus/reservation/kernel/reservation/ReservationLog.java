package com.lawyus.reservation.kernel.reservation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 预约操作留痕（reservation_log）：只追加，每次状态变更或改期写一条。
 */
@Getter
@Setter
@TableName("reservation_log")
public class ReservationLog {

    public static final String ACTION_SUBMIT = "submit";
    public static final String ACTION_CONFIRM = "confirm";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_RESCHEDULE = "reschedule";
    public static final String ACTION_VERIFY = "verify";
    public static final String ACTION_NO_SHOW = "no_show";

    public static final int ROLE_USER = 1;
    public static final int ROLE_TECHNICIAN = 2;
    public static final int ROLE_OWNER = 3;
    public static final int ROLE_SYSTEM = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reservationId;
    private String action;
    private Integer operatorRole;
    private Long operatorId;
    private String detail;
    private LocalDateTime createdAt;
}
