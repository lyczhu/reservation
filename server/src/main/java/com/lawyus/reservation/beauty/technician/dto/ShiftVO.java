package com.lawyus.reservation.beauty.technician.dto;

import com.lawyus.reservation.common.dto.TimeRange;

import java.time.LocalDate;
import java.util.List;

public record ShiftVO(String scope, Integer weekday, LocalDate date, String type, List<TimeRange> intervals) {
}
