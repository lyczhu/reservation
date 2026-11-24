package com.lawyus.reservation.customer.catalog.dto;

/**
 * 服务卡片：列表与热门推荐共用，价格单位为分。
 */
public record ServiceCardVO(
        long id,
        long categoryId,
        String name,
        String cover,
        int durationMin,
        int priceCents,
        int bookedCount) {
}
