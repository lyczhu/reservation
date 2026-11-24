package com.lawyus.reservation.customer.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * C 端门店信息：地址/电话用于导航与拨号，businessHours 覆盖周一至周日（区间为空即休息）。
 */
public record StoreInfoVO(
        String name,
        String logo,
        List<String> banners,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String phone,
        String announcement,
        List<BusinessDayVO> businessHours,
        List<Integer> restDays) {
}
