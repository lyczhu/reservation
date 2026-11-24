package com.lawyus.reservation.kernel.subscribe;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 订阅消息配额：每用户每模板记录剩余可发次数，授权 +N、发送原子 -1。
 */
@Getter
@Setter
@TableName("subscribe_quota")
public class SubscribeQuota {

    public static final int USER_TYPE_USER = 1;
    public static final int USER_TYPE_STAFF = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userType;
    private Long userId;
    private String templateKey;
    private Integer remaining;
    private LocalDateTime updatedAt;
}
