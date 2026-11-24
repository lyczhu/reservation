package com.lawyus.reservation.beauty.technician;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("shift")
public class Shift {

    // scope=week 为周模板（weekday 生效）；scope=date 为日覆盖（date 生效，优先级高于周模板）
    public static final String SCOPE_WEEK = "week";
    public static final String SCOPE_DATE = "date";

    public static final String TYPE_WORK = "work";
    public static final String TYPE_REST = "rest";
    public static final String TYPE_LEAVE = "leave";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long technicianId;
    private String scope;
    private Integer weekday;
    private LocalDate date;
    private String intervals;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
