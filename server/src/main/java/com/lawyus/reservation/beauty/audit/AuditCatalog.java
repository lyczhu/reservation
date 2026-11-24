package com.lawyus.reservation.beauty.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 审计动作目录：动作中文名、业务分类、风险等级与变更字段标签的单一数据源。
 * 查询接口与筛选下拉均从这里取值，避免前后端各维护一份容易漂移的动作表。
 */
public final class AuditCatalog {

    public static final String CATEGORY_SECURITY = "安全与权限";
    public static final String CATEGORY_RULE = "预约规则";
    public static final String CATEGORY_SERVICE = "服务与分类";
    public static final String CATEGORY_TECHNICIAN = "技师与排班";
    public static final String CATEGORY_STORE = "门店设置";

    public static final String RISK_HIGH = "HIGH";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_LOW = "LOW";

    public record ActionMeta(String code, String text, String category, String riskLevel) {
        public String riskText() {
            return switch (riskLevel) {
                case RISK_HIGH -> "高风险";
                case RISK_MEDIUM -> "中风险";
                default -> "低风险";
            };
        }
    }

    private static final Map<String, ActionMeta> ACTIONS = new LinkedHashMap<>();
    private static final Map<String, String> FIELD_LABELS = new LinkedHashMap<>();

    static {
        put("VIEW_PHONE", "查看完整手机号", CATEGORY_SECURITY, RISK_HIGH);
        put("VERIFY_LOCK", "核销锁定", CATEGORY_SECURITY, RISK_HIGH);
        put("ACCOUNT_CREATE", "创建账号", CATEGORY_SECURITY, RISK_HIGH);
        put("ACCOUNT_STATUS", "账号启停", CATEGORY_SECURITY, RISK_HIGH);
        put("PASSWORD_RESET", "重置密码", CATEGORY_SECURITY, RISK_HIGH);
        put("WX_BIND", "绑定微信", CATEGORY_SECURITY, RISK_HIGH);
        put("WX_UNBIND", "解绑微信", CATEGORY_SECURITY, RISK_HIGH);

        put("RULES_UPDATE", "修改预约规则", CATEGORY_RULE, RISK_MEDIUM);

        put("SERVICE_CREATE", "创建服务", CATEGORY_SERVICE, RISK_LOW);
        put("SERVICE_UPDATE", "编辑服务", CATEGORY_SERVICE, RISK_MEDIUM);
        put("SERVICE_STATUS", "服务上下架", CATEGORY_SERVICE, RISK_MEDIUM);
        put("SERVICE_DELETE", "删除服务", CATEGORY_SERVICE, RISK_MEDIUM);
        put("SERVICE_SORT", "服务排序", CATEGORY_SERVICE, RISK_LOW);
        put("CATEGORY_CREATE", "创建分类", CATEGORY_SERVICE, RISK_LOW);
        put("CATEGORY_UPDATE", "编辑分类", CATEGORY_SERVICE, RISK_MEDIUM);
        put("CATEGORY_STATUS", "分类启停", CATEGORY_SERVICE, RISK_MEDIUM);
        put("CATEGORY_DELETE", "删除分类", CATEGORY_SERVICE, RISK_MEDIUM);
        put("CATEGORY_SORT", "分类排序", CATEGORY_SERVICE, RISK_LOW);

        put("TECHNICIAN_CREATE", "创建技师", CATEGORY_TECHNICIAN, RISK_LOW);
        put("TECHNICIAN_UPDATE", "编辑技师", CATEGORY_TECHNICIAN, RISK_MEDIUM);
        put("TECHNICIAN_STATUS", "技师启停", CATEGORY_TECHNICIAN, RISK_MEDIUM);
        put("SHIFT_UPDATE", "排班变更", CATEGORY_TECHNICIAN, RISK_MEDIUM);
        put("SHIFT_COPY", "排班复制", CATEGORY_TECHNICIAN, RISK_LOW);

        put("STORE_UPDATE", "门店资料变更", CATEGORY_STORE, RISK_MEDIUM);

        field("slotGranularity", "时段粒度");
        field("bookableDays", "可预约天数");
        field("minLeadMinutes", "最短提前预约");
        field("cancelDeadlineHours", "取消截止时间");
        field("rescheduleDeadlineHours", "改期截止时间");
        field("needConfirm", "需要店长确认");
        field("bufferMinutes", "时段缓冲");
        field("reminderHours", "到店提醒提前");
        field("name", "名称");
        field("title", "职称");
        field("intro", "简介");
        field("priceCents", "价格");
        field("durationMin", "服务时长");
        field("categoryId", "所属分类");
        field("sort", "排序值");
        field("technicianIds", "服务技师");
        field("status", "状态");
        field("announcement", "门店公告");
        field("wxOpenid", "微信标识");
    }

    private AuditCatalog() {
    }

    private static void put(String action, String text, String category, String riskLevel) {
        ACTIONS.put(action, new ActionMeta(action, text, category, riskLevel));
    }

    private static void field(String key, String label) {
        FIELD_LABELS.put(key, label);
    }

    public static Set<String> categories() {
        return Set.of(CATEGORY_SECURITY, CATEGORY_RULE, CATEGORY_SERVICE, CATEGORY_TECHNICIAN, CATEGORY_STORE);
    }

    public static List<ActionMeta> actions() {
        return new ArrayList<>(ACTIONS.values());
    }

    public static List<String> actionsOfCategory(String category) {
        return ACTIONS.entrySet().stream()
                .filter(entry -> entry.getValue().category().equals(category))
                .map(Map.Entry::getKey)
                .toList();
    }

    public static ActionMeta meta(String action) {
        ActionMeta meta = ACTIONS.get(action);
        return meta == null ? new ActionMeta(action == null ? "" : action,
                action == null ? "" : action, CATEGORY_STORE, RISK_LOW) : meta;
    }

    public static String text(String action) {
        return meta(action).text();
    }

    public static String riskLevel(String action) {
        return meta(action).riskLevel();
    }

    public static String riskText(String action) {
        return meta(action).riskText();
    }

    public static String fieldLabel(String key) {
        return FIELD_LABELS.getOrDefault(key, key);
    }

    /** 把审计详情里的英文键和值转成店长能直接读的单位与文案。 */
    public static String formatValue(String action, String key, String value) {
        if (value == null || value.equals("空")) {
            return "空";
        }
        if (key.equals("needConfirm")) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "1", "true" -> "是";
                case "0", "false" -> "否";
                default -> value;
            };
        }
        if (key.equals("status")) {
            boolean online = value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("ON");
            if (action != null && action.startsWith("SERVICE_")) {
                return online ? "上架" : "下架";
            }
            return online ? "启用" : "停用";
        }
        if (key.equals("priceCents")) {
            try {
                return String.format(Locale.CHINA, "¥%.2f", Long.parseLong(value) / 100.0);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (key.equals("slotGranularity") || lower.endsWith("minutes") || lower.endsWith("min")) {
            return value + " 分钟";
        }
        if (lower.endsWith("hours")) {
            return value + " 小时";
        }
        if (lower.endsWith("days")) {
            return value + " 天";
        }
        return value;
    }
}
