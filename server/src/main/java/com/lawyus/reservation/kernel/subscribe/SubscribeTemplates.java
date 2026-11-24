package com.lawyus.reservation.kernel.subscribe;

/**
 * 订阅模板标识：对应微信公众平台申请的一次性订阅模板，模板 ID 见 app.wx.subscribe 配置。
 */
public final class SubscribeTemplates {

    public static final String RESERVATION_SUCCESS = "reservation_success";
    public static final String RESERVATION_CHANGE = "reservation_change";
    public static final String RESERVATION_REMINDER = "reservation_reminder";
    public static final String NEW_RESERVATION = "new_reservation";

    private SubscribeTemplates() {
    }

    public static boolean isKnown(String key) {
        return RESERVATION_SUCCESS.equals(key) || RESERVATION_CHANGE.equals(key)
                || RESERVATION_REMINDER.equals(key) || NEW_RESERVATION.equals(key);
    }
}
