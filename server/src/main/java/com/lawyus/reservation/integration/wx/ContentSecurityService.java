package com.lawyus.reservation.integration.wx;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.config.AppProperties;
import com.lawyus.reservation.common.exception.BusinessException;
import me.chanjar.weixin.common.error.WxErrorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 图片内容安全（imgSecCheck）：未配置小程序 AppID 时跳过检测；
 * 微信接口调用异常时降级放行并告警，不阻断上传。
 */
@Slf4j
@Service
public class ContentSecurityService {

    private final WxMaService wxUserMaService;
    private final AppProperties properties;

    public ContentSecurityService(@Qualifier("wxUserMaService") WxMaService wxUserMaService,
                                  AppProperties properties) {
        this.wxUserMaService = wxUserMaService;
        this.properties = properties;
    }

    public void ensureImageSafe(byte[] bytes, String extension) {
        if (properties.getWx().getUser().getAppId().isBlank()) {
            return;
        }
        File temp = null;
        try {
            temp = File.createTempFile("reservation-sec-", "." + extension);
            Files.write(temp.toPath(), bytes);
            if (wxUserMaService.getSecurityService().checkImage(temp)) {
                throw new BusinessException(ErrorCode.CONTENT_UNSAFE);
            }
        } catch (WxErrorException e) {
            log.warn("内容安全检测调用失败，降级放行: {}", e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "临时文件写入失败");
        } finally {
            if (temp != null) {
                temp.delete();
            }
        }
    }
}
