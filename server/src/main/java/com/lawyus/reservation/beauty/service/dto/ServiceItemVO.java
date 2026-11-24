package com.lawyus.reservation.beauty.service.dto;

import java.util.List;

public record ServiceItemVO(Long id, Long categoryId, String name, String cover, List<String> images,
                            Integer durationMin, Integer priceCents, String description, String notes,
                            Integer sort, Integer status, Integer bookedCount, List<Long> technicianIds) {
}
