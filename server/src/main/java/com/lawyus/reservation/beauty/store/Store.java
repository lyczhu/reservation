package com.lawyus.reservation.beauty.store;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 门店实体（store，单店版仅一行）；banner 与 businessHours 为 JSON 列文本，经 JsonSupport 转换。
 */
@Getter
@Setter
@TableName("store")
public class Store {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String logo;
    private String banner;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String phone;
    private String businessHours;
    private String announcement;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
