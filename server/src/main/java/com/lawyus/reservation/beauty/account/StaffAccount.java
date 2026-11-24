package com.lawyus.reservation.beauty.account;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("staff_account")
public class StaffAccount {

    public static final int ROLE_OWNER = 1;
    public static final int ROLE_TECHNICIAN = 2;

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private String username;
    private String passwordHash;
    private Integer role;
    private Long technicianId;
    private String wxOpenid;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
