package com.lawyus.reservation.beauty.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.reservation.beauty.account.StaffAccount;
import com.lawyus.reservation.beauty.account.mapper.StaffAccountMapper;
import com.lawyus.reservation.beauty.audit.dto.AuditActionVO;
import com.lawyus.reservation.beauty.audit.dto.AuditChangeVO;
import com.lawyus.reservation.beauty.audit.dto.AuditLogVO;
import com.lawyus.reservation.beauty.audit.dto.AuditQueryResult;
import com.lawyus.reservation.beauty.audit.dto.AuditSummaryVO;
import com.lawyus.reservation.beauty.category.ServiceCategory;
import com.lawyus.reservation.beauty.category.mapper.ServiceCategoryMapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditLog;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.audit.mapper.AuditLogMapper;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.customer.account.User;
import com.lawyus.reservation.customer.account.mapper.UserMapper;

/**
 * 审计日志查询：仅店长可用。把原始审计行加工成店长能直接读懂的时间线数据，
 * 包括动作中文名、业务分类、风险等级、字段级前后对比、友好对象名与筛选概览。
 */
@Service
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int SUMMARY_DETAIL_LIMIT = 120;
    private static final Pattern CHANGE_PATTERN = Pattern.compile("^(.+?)\\s+(.*)->(.*)$");

    private final AuditLogMapper auditLogMapper;
    private final StaffAccountMapper staffAccountMapper;
    private final UserMapper userMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServiceCategoryMapper serviceCategoryMapper;
    private final TechnicianMapper technicianMapper;

    public AuditQueryService(AuditLogMapper auditLogMapper,
                             StaffAccountMapper staffAccountMapper,
                             UserMapper userMapper,
                             ServiceItemMapper serviceItemMapper,
                             ServiceCategoryMapper serviceCategoryMapper,
                             TechnicianMapper technicianMapper) {
        this.auditLogMapper = auditLogMapper;
        this.staffAccountMapper = staffAccountMapper;
        this.userMapper = userMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.serviceCategoryMapper = serviceCategoryMapper;
        this.technicianMapper = technicianMapper;
    }

    /** 筛选下拉的可选动作，与详情展示共用同一份动作目录。 */
    public List<AuditActionVO> actions() {
        return AuditCatalog.actions().stream()
                .map(meta -> new AuditActionVO(meta.code(), meta.text(), meta.category(),
                        meta.riskLevel(), meta.riskText()))
                .toList();
    }

    public AuditQueryResult query(String action, String category, Integer operatorType, Long operatorId,
                                  LocalDate from, LocalDate to, String keyword, int page, int size) {
        if (operatorType != null && operatorType != AuditService.OPERATOR_USER
                && operatorType != AuditService.OPERATOR_STAFF) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "operatorType 仅支持 1-2");
        }
        if (operatorId != null && operatorId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "operatorId 无效");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        if (category != null && !AuditCatalog.categories().contains(category)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "业务分类无效");
        }
        String actionFilter = trimToNull(action);
        String categoryFilter = trimToNull(category);
        String keywordFilter = trimToNull(keyword);
        if (actionFilter != null && actionFilter.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "action 长度不能超过 50");
        }
        if (keywordFilter != null && keywordFilter.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "keyword 长度不能超过 50");
        }
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Set<Long> staffIdsByKeyword = Set.of();
        Set<Long> userIdsByKeyword = Set.of();
        if (keywordFilter != null) {
            staffIdsByKeyword = staffAccountMapper.selectList(new LambdaQueryWrapper<StaffAccount>()
                            .eq(StaffAccount::getStoreId, AppConstants.STORE_ID)
                            .like(StaffAccount::getUsername, keywordFilter))
                    .stream().map(StaffAccount::getId).collect(Collectors.toSet());
            userIdsByKeyword = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .like(User::getNickname, keywordFilter))
                    .stream().map(User::getId).collect(Collectors.toSet());
        }

        Page<AuditLog> pageResult = auditLogMapper.selectPage(new Page<>(current, pageSize),
                filterWrapper(actionFilter, categoryFilter, operatorType, operatorId, from, to, keywordFilter,
                        staffIdsByKeyword, userIdsByKeyword));

        Set<Long> staffIds = operatorIds(pageResult.getRecords(), AuditService.OPERATOR_STAFF);
        Set<Long> userIds = operatorIds(pageResult.getRecords(), AuditService.OPERATOR_USER);
        Set<Long> serviceIds = targetIds(pageResult.getRecords(), "service");
        Set<Long> categoryIds = targetIds(pageResult.getRecords(), "category");
        Set<Long> technicianIds = targetIds(pageResult.getRecords(), "technician");
        Map<Long, String> staffNames = loadNames(
                staffIds.isEmpty() ? List.of() : staffAccountMapper.selectByIds(staffIds),
                StaffAccount::getId, StaffAccount::getUsername);
        Map<Long, String> userNames = loadNames(
                userIds.isEmpty() ? List.of() : userMapper.selectByIds(userIds),
                User::getId, user -> user.getNickname() == null ? "" : user.getNickname());
        Map<Long, String> serviceNames = loadNames(
                serviceIds.isEmpty() ? List.of() : serviceItemMapper.selectByIds(serviceIds),
                ServiceItem::getId, ServiceItem::getName);
        Map<Long, String> categoryNames = loadNames(
                categoryIds.isEmpty() ? List.of() : serviceCategoryMapper.selectByIds(categoryIds),
                ServiceCategory::getId, ServiceCategory::getName);
        Map<Long, String> technicianNames = loadNames(
                technicianIds.isEmpty() ? List.of() : technicianMapper.selectByIds(technicianIds),
                Technician::getId, Technician::getName);

        List<AuditLogVO> records = pageResult.getRecords().stream()
                .map(log -> toVO(log, staffNames, userNames, serviceNames, categoryNames, technicianNames))
                .toList();

        // 单店审计量有限，概览用同一筛选条件下的一次查询统计，保证各卡片口径一致。
        List<AuditLog> filtered = auditLogMapper.selectList(
                filterWrapper(actionFilter, categoryFilter, operatorType, operatorId, from, to, keywordFilter,
                        staffIdsByKeyword, userIdsByKeyword));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        AuditSummaryVO summary = new AuditSummaryVO(
                pageResult.getTotal(),
                filtered.stream().filter(log -> AuditCatalog.RISK_HIGH.equals(AuditCatalog.riskLevel(log.getAction()))).count(),
                filtered.stream().filter(log -> !log.getCreatedAt().isBefore(todayStart)).count(),
                filtered.stream().filter(log -> !log.getCreatedAt().isBefore(weekStart)).count(),
                (int) filtered.stream()
                        .map(log -> log.getOperatorType() + ":" + log.getOperatorId())
                        .collect(Collectors.toSet()).size());

        return new AuditQueryResult(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize(), summary);
    }

    private LambdaQueryWrapper<AuditLog> filterWrapper(String action, String category, Integer operatorType,
                                                        Long operatorId, LocalDate from, LocalDate to, String keyword,
                                                        Set<Long> staffIdsByKeyword, Set<Long> userIdsByKeyword) {
        boolean hasKeyword = keyword != null;
        return new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getStoreId, AppConstants.STORE_ID)
                .eq(action != null, AuditLog::getAction, action)
                .in(category != null, AuditLog::getAction,
                        category == null ? List.of() : AuditCatalog.actionsOfCategory(category))
                .eq(operatorType != null, AuditLog::getOperatorType, operatorType)
                .eq(operatorId != null, AuditLog::getOperatorId, operatorId)
                .ge(from != null, AuditLog::getCreatedAt, from == null ? null : from.atStartOfDay())
                .lt(to != null, AuditLog::getCreatedAt, to == null ? null : to.plusDays(1).atStartOfDay())
                .and(hasKeyword, wrapper -> {
                    String upperKeyword = keyword.toUpperCase(Locale.ROOT);
                    wrapper.like(AuditLog::getDetail, keyword)
                            .or().like(AuditLog::getTarget, keyword)
                            .or().like(AuditLog::getAction, upperKeyword);
                    if (!staffIdsByKeyword.isEmpty()) {
                        wrapper.or().eq(AuditLog::getOperatorType, AuditService.OPERATOR_STAFF)
                                .in(AuditLog::getOperatorId, staffIdsByKeyword);
                    }
                    if (!userIdsByKeyword.isEmpty()) {
                        wrapper.or().eq(AuditLog::getOperatorType, AuditService.OPERATOR_USER)
                                .in(AuditLog::getOperatorId, userIdsByKeyword);
                    }
                })
                .orderByDesc(AuditLog::getId);
    }

    private Set<Long> operatorIds(List<AuditLog> logs, int operatorType) {
        return logs.stream()
                .filter(log -> log.getOperatorType() == operatorType)
                .map(AuditLog::getOperatorId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> targetIds(List<AuditLog> logs, String prefix) {
        String marker = prefix + ":";
        return logs.stream()
                .map(AuditLog::getTarget)
                .filter(target -> target != null && target.startsWith(marker))
                .map(target -> {
                    try {
                        return Optional.of(Long.valueOf(target.substring(marker.length())));
                    } catch (NumberFormatException e) {
                        return Optional.<Long>empty();
                    }
                })
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T> Map<Long, String> loadNames(List<T> entities,
                                            java.util.function.Function<T, Long> idGetter,
                                            java.util.function.Function<T, String> nameGetter) {
        return entities.stream()
                .filter(entity -> idGetter.apply(entity) != null && nameGetter.apply(entity) != null)
                .collect(Collectors.toMap(idGetter, nameGetter, (a, b) -> a));
    }

    private AuditLogVO toVO(AuditLog log, Map<Long, String> staffNames, Map<Long, String> userNames,
                            Map<Long, String> serviceNames, Map<Long, String> categoryNames,
                            Map<Long, String> technicianNames) {
        boolean staff = log.getOperatorType() == AuditService.OPERATOR_STAFF;
        String operatorName = staff ? staffNames.get(log.getOperatorId()) : userNames.get(log.getOperatorId());
        List<AuditChangeVO> changes = parseChanges(log.getAction(), log.getDetail());
        AuditCatalog.ActionMeta meta = AuditCatalog.meta(log.getAction());
        return new AuditLogVO(
                log.getId(),
                log.getOperatorType(),
                staff ? "商家账号" : "顾客",
                log.getOperatorId(),
                operatorName,
                log.getAction(),
                meta.text(),
                log.getTarget(),
                targetText(log.getTarget(), serviceNames, categoryNames, technicianNames),
                log.getDetail(),
                log.getIp(),
                ipText(log.getIp()),
                log.getCreatedAt(),
                meta.category(),
                meta.riskLevel(),
                meta.riskText(),
                summaryText(log.getDetail(), changes),
                changes);
    }

    private List<AuditChangeVO> parseChanges(String action, String detail) {
        if (detail == null || detail.isBlank()) {
            return List.of();
        }
        List<AuditChangeVO> changes = new java.util.ArrayList<>();
        for (String segment : detail.split(";")) {
            Matcher matcher = CHANGE_PATTERN.matcher(segment.trim());
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).trim();
            changes.add(new AuditChangeVO(key, AuditCatalog.fieldLabel(key),
                    AuditCatalog.formatValue(action, key, matcher.group(2).trim()),
                    AuditCatalog.formatValue(action, key, matcher.group(3).trim())));
        }
        return changes;
    }

    private String summaryText(String detail, List<AuditChangeVO> changes) {
        if (changes.isEmpty()) {
            String text = Objects.requireNonNullElse(detail, "");
            return text.length() > SUMMARY_DETAIL_LIMIT ? text.substring(0, SUMMARY_DETAIL_LIMIT) + "…" : text;
        }
        String head = changes.stream().limit(2)
                .map(change -> change.label() + " " + change.before() + " → " + change.after())
                .collect(Collectors.joining("；"));
        return changes.size() > 2 ? head + "，等 " + changes.size() + " 项变更" : head;
    }

    private String targetText(String target, Map<Long, String> serviceNames,
                              Map<Long, String> categoryNames, Map<Long, String> technicianNames) {
        if (target == null || target.isBlank()) {
            return "—";
        }
        return switch (target) {
            case "rule_config" -> "预约规则";
            case "staff_account" -> "员工账号";
            case "reservation" -> "预约单";
            case "service" -> "服务列表";
            case "category" -> "分类列表";
            default -> namedTarget(target, "service:", serviceNames, "服务")
                    .or(() -> namedTarget(target, "category:", categoryNames, "分类"))
                    .or(() -> namedTarget(target, "technician:", technicianNames, "技师"))
                    .orElse(target);
        };
    }

    private Optional<String> namedTarget(String target, String prefix, Map<Long, String> names, String label) {
        if (!target.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(target.substring(prefix.length()));
            String name = names.get(id);
            return Optional.of(name == null ? label + " #" + id : label + "「" + name + "」");
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String ipText(String ip) {
        if (ip == null || ip.isBlank()) {
            return "未记录";
        }
        if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("127.0.0.1")) {
            return "本机 · " + ip;
        }
        return ip;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
