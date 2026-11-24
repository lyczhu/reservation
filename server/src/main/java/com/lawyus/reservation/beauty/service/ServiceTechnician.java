package com.lawyus.reservation.beauty.service;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 服务-技师关联（service_technician），可约技师关系的唯一事实来源。
 */
@Getter
@Setter
@TableName("service_technician")
public class ServiceTechnician {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long serviceId;
    private Long technicianId;
    private LocalDateTime createdAt;
}
