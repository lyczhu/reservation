package com.lawyus.reservation.beauty.service;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 服务实体，映射表 service（类名避开 Spring @Service 注解命名冲突）；images 为 JSON 列文本。
 */
@Getter
@Setter
@TableName("service")
public class ServiceItem {

    public static final int STATUS_ON = 1;
    public static final int STATUS_OFF = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private Long categoryId;
    private String name;
    private String cover;
    private String images;
    private Integer durationMin;
    private Integer priceCents;
    private String description;
    private String notes;
    private Integer sort;
    private Integer status;
    private Integer deleted;
    private Integer bookedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
