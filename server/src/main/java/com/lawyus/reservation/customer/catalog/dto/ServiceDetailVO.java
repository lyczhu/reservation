package com.lawyus.reservation.customer.catalog.dto;

import java.util.List;

/**
 * 服务详情：含图片轮播、说明、注意事项与可约技师（仅启用且关联该服务）。
 */
public record ServiceDetailVO(
        long id,
        long categoryId,
        String categoryName,
        String name,
        String cover,
        List<String> images,
        int durationMin,
        int priceCents,
        String description,
        String notes,
        int bookedCount,
        List<TechnicianCardVO> technicians) {
}
