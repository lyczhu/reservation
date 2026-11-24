package com.lawyus.reservation.integration.wx.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 订阅授权上报：一次性订阅每授权一次可发一条，count 为本次接受次数（勾选“保持授权”可累计）。
 */
public record SubscribeAuthRequest(
        @NotEmpty @Size(max = 4) List<@Valid TemplateCount> templates) {

    public record TemplateCount(
            @NotBlank String key,
            @Min(1) @Max(5) int count) {
    }
}
