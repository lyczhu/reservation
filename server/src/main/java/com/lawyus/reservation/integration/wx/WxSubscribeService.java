package com.lawyus.reservation.integration.wx;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.account.StaffAccount;
import com.lawyus.reservation.beauty.account.mapper.StaffAccountMapper;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.config.AppProperties;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.customer.account.User;
import com.lawyus.reservation.customer.account.mapper.UserMapper;
import com.lawyus.reservation.integration.wx.dto.SubscribeAuthRequest;
import com.lawyus.reservation.kernel.subscribe.SubscribeQuota;
import com.lawyus.reservation.kernel.subscribe.SubscribeTemplates;
import com.lawyus.reservation.kernel.subscribe.mapper.SubscribeQuotaMapper;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;

/**
 * 一次性订阅消息：配额授权与发送。
 * 发送前原子扣减 remaining（>0 才发），微信接口失败重试 3 次，仍失败回补配额并落日志；
 * 模板 ID 未配置时降级为日志记录（开发环境无微信凭证），配额流转仍完整执行。
 */
@Slf4j
@Service
public class WxSubscribeService {

    private static final int MAX_RETRY = 3;

    private final SubscribeQuotaMapper quotaMapper;
    private final UserMapper userMapper;
    private final StaffAccountMapper staffAccountMapper;
    private final WxMaService wxUserMaService;
    private final WxMaService wxStaffMaService;
    private final AppProperties properties;

    public WxSubscribeService(SubscribeQuotaMapper quotaMapper,
                              UserMapper userMapper,
                              StaffAccountMapper staffAccountMapper,
                              WxMaService wxUserMaService,
                              WxMaService wxStaffMaService,
                              AppProperties properties) {
        this.quotaMapper = quotaMapper;
        this.userMapper = userMapper;
        this.staffAccountMapper = staffAccountMapper;
        this.wxUserMaService = wxUserMaService;
        this.wxStaffMaService = wxStaffMaService;
        this.properties = properties;
    }

    public void authorize(int userType, long userId, List<SubscribeAuthRequest.TemplateCount> templates) {
        for (SubscribeAuthRequest.TemplateCount template : templates) {
            if (!SubscribeTemplates.isKnown(template.key())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "未知的订阅模板：" + template.key());
            }
            if (increase(userType, userId, template.key(), template.count()) == 0) {
                SubscribeQuota quota = new SubscribeQuota();
                quota.setUserType(userType);
                quota.setUserId(userId);
                quota.setTemplateKey(template.key());
                quota.setRemaining(template.count());
                try {
                    quotaMapper.insert(quota);
                } catch (DuplicateKeyException e) {
                    increase(userType, userId, template.key(), template.count());
                }
            }
        }
    }

    public void send(int userType, long userId, String templateKey, String page,
                     List<WxMaSubscribeMessage.MsgData> data) {
        String openid = openid(userType, userId);
        if (openid == null || openid.isBlank()) {
            log.info("[订阅] 接收方 {}-{} 无 openid，跳过 template={}", userType, userId, templateKey);
            return;
        }
        int rows = quotaMapper.update(null, new LambdaUpdateWrapper<SubscribeQuota>()
                .setSql("remaining = remaining - 1")
                .eq(SubscribeQuota::getUserType, userType)
                .eq(SubscribeQuota::getUserId, userId)
                .eq(SubscribeQuota::getTemplateKey, templateKey)
                .gt(SubscribeQuota::getRemaining, 0));
        if (rows == 0) {
            log.info("[订阅] 配额不足(42205)，跳过发送 {}-{} template={}", userType, userId, templateKey);
            return;
        }
        String templateId = templateId(templateKey);
        if (templateId.isBlank()) {
            log.info("[订阅-降级] 未配置模板 ID，已记录发送 {}-{} template={} page={} data={}",
                    userType, userId, templateKey, page, summarize(data));
            return;
        }
        WxMaSubscribeMessage message = new WxMaSubscribeMessage();
        message.setToUser(openid);
        message.setTemplateId(templateId);
        message.setPage(page);
        message.setData(data);
        WxMaService service = userType == SubscribeQuota.USER_TYPE_STAFF ? wxStaffMaService : wxUserMaService;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                service.getSubscribeService().sendSubscribeMsg(message);
                log.info("[订阅] 已发送 {}-{} template={}", userType, userId, templateKey);
                return;
            } catch (Exception e) {
                log.warn("[订阅] 发送失败 {}/{} {}-{} template={}: {}",
                        attempt, MAX_RETRY, userType, userId, templateKey, e.getMessage());
            }
        }
        increase(userType, userId, templateKey, 1);
        log.error("[订阅] 重试仍失败，已回补配额 {}-{} template={}", userType, userId, templateKey);
    }

    private int increase(int userType, long userId, String key, int count) {
        return quotaMapper.update(null, new LambdaUpdateWrapper<SubscribeQuota>()
                .setSql("remaining = remaining + " + count)
                .eq(SubscribeQuota::getUserType, userType)
                .eq(SubscribeQuota::getUserId, userId)
                .eq(SubscribeQuota::getTemplateKey, key));
    }

    private String openid(int userType, long userId) {
        if (userType == SubscribeQuota.USER_TYPE_STAFF) {
            StaffAccount account = staffAccountMapper.selectById(userId);
            return account == null ? null : account.getWxOpenid();
        }
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getOpenid();
    }

    private String templateId(String key) {
        AppProperties.Subscribe subscribe = properties.getWx().getSubscribe();
        return switch (key) {
            case SubscribeTemplates.RESERVATION_SUCCESS -> subscribe.getSuccessTemplateId();
            case SubscribeTemplates.RESERVATION_CHANGE -> subscribe.getChangeTemplateId();
            case SubscribeTemplates.RESERVATION_REMINDER -> subscribe.getReminderTemplateId();
            case SubscribeTemplates.NEW_RESERVATION -> subscribe.getNewReservationTemplateId();
            default -> "";
        };
    }

    private String summarize(List<WxMaSubscribeMessage.MsgData> data) {
        return data.stream().map(item -> item.getName() + "=" + item.getValue()).toList().toString();
    }
}
