package com.lawyus.reservation.beauty.category;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 服务分类实体（service_category），软删除由 deleted 标记。
 */
@Getter
@Setter
@TableName("service_category")
public class ServiceCategory {

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private String name;
    private String icon;
    private Integer sort;
    private Integer status;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
