package com.lawyus.reservation.beauty.account.dto;

public record StaffLoginResponse(String token, String role, long staffId, Long technicianId) {
}
