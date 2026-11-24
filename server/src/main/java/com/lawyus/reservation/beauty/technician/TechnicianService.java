package com.lawyus.reservation.beauty.technician;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.account.StaffAccount;
import com.lawyus.reservation.beauty.account.mapper.StaffAccountMapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.service.ServiceTechnician;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.service.mapper.ServiceTechnicianMapper;
import com.lawyus.reservation.beauty.technician.dto.ShiftCopyRequest;
import com.lawyus.reservation.beauty.technician.dto.ShiftEntryRequest;
import com.lawyus.reservation.beauty.technician.dto.ShiftUpdateRequest;
import com.lawyus.reservation.beauty.technician.dto.ShiftVO;
import com.lawyus.reservation.beauty.technician.dto.TechnicianRequest;
import com.lawyus.reservation.beauty.technician.dto.TechnicianShiftsVO;
import com.lawyus.reservation.beauty.technician.dto.TechnicianVO;
import com.lawyus.reservation.beauty.technician.mapper.ShiftMapper;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.common.security.StpStaffUtil;
import com.lawyus.reservation.common.validation.TimeRangeValidator;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 技师档案与排班管理。排班分周模板与日覆盖两级，日覆盖优先；停用技师前需确认没有未完成的预约。
 */
@Service
public class TechnicianService {

    private static final Set<String> SHIFT_TYPES = Set.of(Shift.TYPE_WORK, Shift.TYPE_REST, Shift.TYPE_LEAVE);
    private static final int MAX_SHIFT_RANGE_DAYS = 90;

    private final TechnicianMapper technicianMapper;
    private final ShiftMapper shiftMapper;
    private final StaffAccountMapper staffAccountMapper;
    private final ServiceTechnicianMapper serviceTechnicianMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ReservationMapper reservationMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JsonSupport jsonSupport;
    private final AuditService auditService;

    public TechnicianService(TechnicianMapper technicianMapper,
                             ShiftMapper shiftMapper,
                             StaffAccountMapper staffAccountMapper,
                             ServiceTechnicianMapper serviceTechnicianMapper,
                             ServiceItemMapper serviceItemMapper,
                             ReservationMapper reservationMapper,
                             BCryptPasswordEncoder passwordEncoder,
                             JsonSupport jsonSupport,
                             AuditService auditService) {
        this.technicianMapper = technicianMapper;
        this.shiftMapper = shiftMapper;
        this.staffAccountMapper = staffAccountMapper;
        this.serviceTechnicianMapper = serviceTechnicianMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.reservationMapper = reservationMapper;
        this.passwordEncoder = passwordEncoder;
        this.jsonSupport = jsonSupport;
        this.auditService = auditService;
    }

    public List<TechnicianVO> list() {
        List<Technician> technicians = technicianMapper.selectList(new LambdaQueryWrapper<Technician>()
                .orderByAsc(Technician::getId));
        if (technicians.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> serviceNames = serviceNamesByTechnician();
        Map<Long, String> accountNames = staffAccountMapper.selectList(new LambdaQueryWrapper<StaffAccount>()
                        .eq(StaffAccount::getRole, StaffAccount.ROLE_TECHNICIAN)).stream()
                .filter(account -> account.getTechnicianId() != null)
                .collect(Collectors.toMap(StaffAccount::getTechnicianId, StaffAccount::getUsername, (a, b) -> a));
        return technicians.stream()
                .map(technician -> new TechnicianVO(technician.getId(), technician.getName(),
                        technician.getAvatar(), technician.getTitle(), technician.getIntro(), technician.getStatus(),
                        serviceNames.getOrDefault(technician.getId(), List.of()),
                        accountNames.get(technician.getId())))
                .toList();
    }

    @Transactional
    public TechnicianVO create(TechnicianRequest request) {
        Technician technician = new Technician();
        technician.setStoreId(AppConstants.STORE_ID);
        technician.setName(request.name());
        technician.setAvatar(request.avatar());
        technician.setTitle(request.title());
        technician.setIntro(request.intro());
        technician.setStatus(Technician.STATUS_ENABLED);
        technician.setDeleted(0);
        technicianMapper.insert(technician);
        createAccountIfRequested(technician.getId(), request);
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "TECHNICIAN_CREATE",
                "technician:" + technician.getId(), "创建技师 " + technician.getName());
        return new TechnicianVO(technician.getId(), technician.getName(), technician.getAvatar(),
                technician.getTitle(), technician.getIntro(), technician.getStatus(),
                List.of(), request.accountUsername());
    }

    @Transactional
    public void update(long id, TechnicianRequest request) {
        Technician old = getExisting(id);
        technicianMapper.update(null, new LambdaUpdateWrapper<Technician>()
                .eq(Technician::getId, id)
                .set(Technician::getName, request.name())
                .set(Technician::getAvatar, request.avatar())
                .set(Technician::getTitle, request.title())
                .set(Technician::getIntro, request.intro()));
        boolean hasAccount = staffAccountMapper.selectCount(new LambdaQueryWrapper<StaffAccount>()
                .eq(StaffAccount::getTechnicianId, id)) > 0;
        if (!hasAccount) {
            createAccountIfRequested(id, request);
        }
        StringBuilder detail = new StringBuilder();
        AuditService.appendChange(detail, "name", old.getName(), request.name());
        AuditService.appendChange(detail, "title", old.getTitle(), request.title());
        AuditService.appendChange(detail, "intro", old.getIntro(), request.intro());
        if (detail.length() > 0) {
            auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "TECHNICIAN_UPDATE",
                    "technician:" + id, detail.toString());
        }
    }

    public void updateStatus(long id, int status) {
        Technician old = getExisting(id);
        if (status == Technician.STATUS_DISABLED) {
            LocalDate today = LocalDate.now();
            long activeCount = reservationMapper.countActiveBetween(id, today, today.plusDays(30));
            if (activeCount > 0) {
                throw new BusinessException(ErrorCode.ILLEGAL_STATE, "该技师存在未来活跃预约，请先处理后再停用");
            }
        }
        Technician update = new Technician();
        update.setId(id);
        update.setStatus(status);
        technicianMapper.updateById(update);
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "TECHNICIAN_STATUS",
                "technician:" + id, "技师 " + old.getName() + " status " + old.getStatus() + "->" + status);
    }

    public TechnicianShiftsVO getShifts(long technicianId, LocalDate from, LocalDate to) {
        getExisting(technicianId);
        Map<Integer, Shift> weekMap = shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                        .eq(Shift::getTechnicianId, technicianId)
                        .eq(Shift::getScope, Shift.SCOPE_WEEK)).stream()
                .filter(shift -> shift.getWeekday() != null)
                .collect(Collectors.toMap(Shift::getWeekday, Function.identity(), (a, b) -> a));
        List<ShiftVO> weekTemplate = IntStream.rangeClosed(1, 7)
                .mapToObj(weekday -> {
                    Shift shift = weekMap.get(weekday);
                    if (shift == null) {
                        return new ShiftVO(Shift.SCOPE_WEEK, weekday, null, Shift.TYPE_REST, List.of());
                    }
                    return toVO(shift);
                })
                .toList();

        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(27) : to;
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "查询结束日期不能早于开始日期");
        }
        if (end.isAfter(start.plusDays(MAX_SHIFT_RANGE_DAYS))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "查询范围不能超过 90 天");
        }
        List<ShiftVO> overrides = shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                        .eq(Shift::getTechnicianId, technicianId)
                        .eq(Shift::getScope, Shift.SCOPE_DATE)
                        .between(Shift::getDate, start, end)
                        .orderByAsc(Shift::getDate)).stream()
                .map(this::toVO)
                .toList();
        return new TechnicianShiftsVO(technicianId, weekTemplate, overrides);
    }

    @Transactional
    public void updateShifts(long technicianId, ShiftUpdateRequest request) {
        Technician technician = getExisting(technicianId);
        for (ShiftEntryRequest entry : request.entries()) {
            validateEntry(entry);
            Shift existing = findShift(technicianId, entry);
            if (existing == null) {
                Shift shift = new Shift();
                shift.setTechnicianId(technicianId);
                shift.setScope(entry.scope());
                shift.setWeekday(entry.weekday());
                shift.setDate(entry.date());
                shift.setType(entry.type());
                shift.setIntervals(jsonSupport.write(entry.intervals() == null ? List.of() : entry.intervals()));
                shiftMapper.insert(shift);
            } else {
                Shift update = new Shift();
                update.setId(existing.getId());
                update.setType(entry.type());
                update.setIntervals(jsonSupport.write(entry.intervals() == null ? List.of() : entry.intervals()));
                shiftMapper.updateById(update);
            }
        }
        long weekCount = request.entries().stream().filter(entry -> Shift.SCOPE_WEEK.equals(entry.scope())).count();
        long dateCount = request.entries().size() - weekCount;
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "SHIFT_UPDATE",
                "technician:" + technicianId, "技师 " + technician.getName() + " 更新排班 "
                        + request.entries().size() + " 条（周模板 " + weekCount + " / 日覆盖 " + dateCount + "）");
    }

    @Transactional
    public void copyShifts(long technicianId, ShiftCopyRequest request) {
        Technician technician = getExisting(technicianId);
        LocalDate sourceStart = request.sourceWeekStart();
        if (sourceStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "sourceWeekStart 必须是周一");
        }
        List<Shift> source = shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                .eq(Shift::getTechnicianId, technicianId)
                .eq(Shift::getScope, Shift.SCOPE_DATE)
                .between(Shift::getDate, sourceStart, sourceStart.plusDays(6)));
        if (source.isEmpty()) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "源周没有日覆盖排班可复制");
        }
        for (int week = 1; week <= request.weeks(); week++) {
            for (Shift sourceShift : source) {
                LocalDate targetDate = sourceShift.getDate().plusWeeks(week);
                Shift existing = shiftMapper.selectOne(new LambdaQueryWrapper<Shift>()
                        .eq(Shift::getTechnicianId, technicianId)
                        .eq(Shift::getScope, Shift.SCOPE_DATE)
                        .eq(Shift::getDate, targetDate));
                if (existing == null) {
                    Shift copy = new Shift();
                    copy.setTechnicianId(technicianId);
                    copy.setScope(Shift.SCOPE_DATE);
                    copy.setDate(targetDate);
                    copy.setType(sourceShift.getType());
                    copy.setIntervals(sourceShift.getIntervals());
                    shiftMapper.insert(copy);
                } else {
                    Shift update = new Shift();
                    update.setId(existing.getId());
                    update.setType(sourceShift.getType());
                    update.setIntervals(sourceShift.getIntervals());
                    shiftMapper.updateById(update);
                }
            }
        }
        auditService.record(AuditService.OPERATOR_STAFF, StpStaffUtil.getStaffId(), "SHIFT_COPY",
                "technician:" + technicianId, "技师 " + technician.getName() + " 复制排班 "
                        + request.weeks() + " 周，每周 " + source.size() + " 条");
    }

    private void createAccountIfRequested(long technicianId, TechnicianRequest request) {
        if (request.accountUsername() == null || request.accountUsername().isBlank()) {
            return;
        }
        if (request.accountPassword() == null || request.accountPassword().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "创建账号时密码必填");
        }
        StaffAccount account = new StaffAccount();
        account.setStoreId(AppConstants.STORE_ID);
        account.setUsername(request.accountUsername());
        account.setPasswordHash(passwordEncoder.encode(request.accountPassword()));
        account.setRole(StaffAccount.ROLE_TECHNICIAN);
        account.setTechnicianId(technicianId);
        account.setStatus(StaffAccount.STATUS_ENABLED);
        try {
            staffAccountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "登录名已存在或该技师已绑定账号");
        }
    }

    private void validateEntry(ShiftEntryRequest entry) {
        String scope = entry.scope();
        if (!Shift.SCOPE_WEEK.equals(scope) && !Shift.SCOPE_DATE.equals(scope)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "scope 仅支持 week/date");
        }
        if (!SHIFT_TYPES.contains(entry.type())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "type 仅支持 work/rest/leave");
        }
        if (Shift.SCOPE_WEEK.equals(scope)) {
            if (entry.weekday() == null || entry.weekday() < 1 || entry.weekday() > 7) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "weekday 必须为 1-7");
            }
            if (entry.date() != null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "week 模式不可传 date");
            }
        } else if (entry.date() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "date 模式必须传 date");
        }
        if (Shift.TYPE_WORK.equals(entry.type())) {
            if (entry.intervals() == null || entry.intervals().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "工作日必须设置班次时段");
            }
            TimeRangeValidator.validate(entry.intervals());
        }
    }

    private Shift findShift(long technicianId, ShiftEntryRequest entry) {
        LambdaQueryWrapper<Shift> wrapper = new LambdaQueryWrapper<Shift>()
                .eq(Shift::getTechnicianId, technicianId)
                .eq(Shift::getScope, entry.scope());
        if (Shift.SCOPE_WEEK.equals(entry.scope())) {
            wrapper.eq(Shift::getWeekday, entry.weekday());
        } else {
            wrapper.eq(Shift::getDate, entry.date());
        }
        return shiftMapper.selectOne(wrapper);
    }

    private Technician getExisting(long id) {
        Technician technician = technicianMapper.selectById(id);
        if (technician == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "技师不存在");
        }
        return technician;
    }

    private Map<Long, List<String>> serviceNamesByTechnician() {
        List<ServiceTechnician> relations = serviceTechnicianMapper.selectList(null);
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> serviceIds = relations.stream().map(ServiceTechnician::getServiceId).collect(Collectors.toSet());
        Map<Long, String> names = serviceItemMapper.selectByIds(serviceIds).stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));
        return relations.stream()
                .filter(relation -> names.containsKey(relation.getServiceId()))
                .collect(Collectors.groupingBy(ServiceTechnician::getTechnicianId,
                        Collectors.mapping(relation -> Objects.requireNonNull(names.get(relation.getServiceId())),
                                Collectors.toList())));
    }

    private ShiftVO toVO(Shift shift) {
        List<TimeRange> intervals = jsonSupport.read(shift.getIntervals(), new TypeReference<>() {
        });
        return new ShiftVO(shift.getScope(), shift.getWeekday(), shift.getDate(), shift.getType(),
                intervals == null ? List.of() : intervals);
    }
}
