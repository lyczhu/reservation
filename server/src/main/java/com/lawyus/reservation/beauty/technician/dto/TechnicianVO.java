package com.lawyus.reservation.beauty.technician.dto;

import java.util.List;

public record TechnicianVO(Long id, String name, String avatar, String title, String intro, Integer status,
                           List<String> services, String accountUsername) {
}
