package com.lawyus.reservation.beauty.reservation.dto;

import java.time.LocalDate;

/**
 * 核销结果：alreadyCompleted 标识重复核销按幂等成功处理。
 */
public record VerifyResultVO(
        long id,
        String no,
        int status,
        String statusText,
        String serviceName,
        String technicianName,
        LocalDate date,
        String startTime,
        String endTime,
        String contactName,
        boolean alreadyCompleted) {
}
