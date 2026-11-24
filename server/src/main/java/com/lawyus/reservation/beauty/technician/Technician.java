package com.lawyus.reservation.beauty.technician;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 技师档案（technician）；擅长服务不落本表，由 service_technician 推导展示。
 */
@Getter
@Setter
@TableName("technician")
public class Technician {

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private String name;
    private String avatar;
    private String title;
    private String intro;
    private Integer status;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
