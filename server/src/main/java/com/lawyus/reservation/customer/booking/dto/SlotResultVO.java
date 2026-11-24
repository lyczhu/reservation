package com.lawyus.reservation.customer.booking.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 时段查询结果：minLeadMinutes 用于前端区分「已过」与「未到但不足最小提前量」。
 */
public record SlotResultVO(long serviceId, LocalDate date, int durationMin, int granularity,
                           int minLeadMinutes, List<SlotVO> slots) {
}
