package com.lawyus.reservation.customer.account;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 顾客账号：以微信 openid 唯一，手机号仅存 AES 密文与 HMAC 哈希，不存明文。
 */
@Getter
@Setter
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String nickname;
    private String avatar;
    private String phone;
    private String phoneHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
