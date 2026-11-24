package com.lawyus.reservation.customer.catalog.dto;

import java.util.List;

import com.lawyus.reservation.common.dto.TimeRange;

/**
 * 单日营业时段：weekday 1-7，intervals 为空表示休息日。
 */
public record BusinessDayVO(int weekday, String label, List<TimeRange> intervals) {
}
