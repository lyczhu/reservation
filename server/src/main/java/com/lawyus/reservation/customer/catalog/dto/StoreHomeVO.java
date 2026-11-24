package com.lawyus.reservation.customer.catalog.dto;

import java.util.List;

/**
 * 首页聚合：门店信息 + 分类导航 + 热门服务 + 可约天数 + 休息日提示公告（置顶展示）。
 */
public record StoreHomeVO(
        StoreInfoVO store,
        List<CategoryVO> categories,
        List<ServiceCardVO> hotServices,
        int bookableDays,
        String notice) {
}
