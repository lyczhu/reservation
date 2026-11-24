package com.lawyus.reservation.kernel.slot;

import lombok.Getter;

/**
 * 时段可约状态。已过与后台排班无关，按当前时间判定；休息表示不在技师班次内。
 */
@Getter
public enum SlotState {

    AVAILABLE("available"),
    FULL("full"),
    PAST("past"),
    REST("rest");

    private final String code;

    SlotState(String code) {
        this.code = code;
    }
}
