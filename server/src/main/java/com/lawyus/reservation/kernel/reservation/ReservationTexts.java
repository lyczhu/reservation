package com.lawyus.reservation.kernel.reservation;

/**
 * 预约状态、来源与留痕动作的中文展示文案，顾客端与商家端共用。
 */
public final class ReservationTexts {

    private ReservationTexts() {
    }

    public static String status(int status) {
        return switch (status) {
            case Reservation.STATUS_WAIT_CONFIRM -> "待确认";
            case Reservation.STATUS_WAIT_SERVICE -> "待服务";
            case Reservation.STATUS_COMPLETED -> "已完成";
            case Reservation.STATUS_CANCELLED -> "已取消";
            case Reservation.STATUS_NO_SHOW -> "未到店";
            default -> "";
        };
    }

    public static String source(int source) {
        return source == Reservation.SOURCE_PROXY ? "代客" : "自助";
    }

    public static String action(String action) {
        return switch (action) {
            case ReservationLog.ACTION_SUBMIT -> "提交预约";
            case ReservationLog.ACTION_CONFIRM -> "商家确认";
            case ReservationLog.ACTION_REJECT -> "商家拒绝";
            case ReservationLog.ACTION_CANCEL -> "取消预约";
            case ReservationLog.ACTION_RESCHEDULE -> "改期";
            case ReservationLog.ACTION_VERIFY -> "完成核销";
            case ReservationLog.ACTION_NO_SHOW -> "标记未到店";
            default -> action;
        };
    }

    /** 代客来源的提交动作展示为“代客预约”。 */
    public static String action(String action, int source) {
        if (ReservationLog.ACTION_SUBMIT.equals(action) && source == Reservation.SOURCE_PROXY) {
            return "代客预约";
        }
        return action(action);
    }
}
