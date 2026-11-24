package com.lawyus.reservation.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * HH:mm 时间区间，用于门店营业时间与技师排班时段。
 */
@Getter
@Setter
public class TimeRange {

    private String start;
    private String end;

    public TimeRange() {
    }

    public TimeRange(String start, String end) {
        this.start = start;
        this.end = end;
    }
}
