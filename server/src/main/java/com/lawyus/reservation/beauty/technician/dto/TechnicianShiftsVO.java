package com.lawyus.reservation.beauty.technician.dto;

import java.util.List;

public record TechnicianShiftsVO(Long technicianId, List<ShiftVO> weekTemplate, List<ShiftVO> overrides) {
}
