package com.lawyus.reservation.beauty.store.dto;

import com.lawyus.reservation.common.dto.TimeRange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record StoreVO(Long id, String name, String logo, List<String> banner, String address,
                      BigDecimal lat, BigDecimal lng, String phone,
                      Map<String, List<TimeRange>> businessHours, String announcement, Integer status) {
}
