package com.lawyus.reservation.beauty.technician.dto;

import com.lawyus.reservation.common.dto.TimeRange;

import java.time.LocalDate;
import java.util.List;

/**
 * 单条排班：scope=week 时 weekday（1-7）生效，scope=date 时 date 生效；type=work 必须提供合法 intervals。
 */
public record ShiftEntryRequest(String scope, Integer weekday, LocalDate date, String type,
                                List<TimeRange> intervals) {
}
