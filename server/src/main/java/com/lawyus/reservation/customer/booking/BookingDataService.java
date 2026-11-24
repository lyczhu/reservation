package com.lawyus.reservation.customer.booking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.service.ServiceTechnician;
import com.lawyus.reservation.beauty.service.mapper.ServiceItemMapper;
import com.lawyus.reservation.beauty.service.mapper.ServiceTechnicianMapper;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.store.Store;
import com.lawyus.reservation.beauty.store.mapper.RuleConfigMapper;
import com.lawyus.reservation.beauty.store.mapper.StoreMapper;
import com.lawyus.reservation.beauty.technician.Shift;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.ShiftMapper;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预约数据读取：营业时间、可约技师、有效班次与活跃预约。
 * 有效班次按“日覆盖优先于周模板”解析，非 work 类型视为当日休息。
 */
@Service
public class BookingDataService {

    private final StoreMapper storeMapper;
    private final RuleConfigMapper ruleConfigMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServiceTechnicianMapper serviceTechnicianMapper;
    private final TechnicianMapper technicianMapper;
    private final ShiftMapper shiftMapper;
    private final ReservationMapper reservationMapper;
    private final JsonSupport jsonSupport;

    public BookingDataService(StoreMapper storeMapper,
                              RuleConfigMapper ruleConfigMapper,
                              ServiceItemMapper serviceItemMapper,
                              ServiceTechnicianMapper serviceTechnicianMapper,
                              TechnicianMapper technicianMapper,
                              ShiftMapper shiftMapper,
                              ReservationMapper reservationMapper,
                              JsonSupport jsonSupport) {
        this.storeMapper = storeMapper;
        this.ruleConfigMapper = ruleConfigMapper;
        this.serviceItemMapper = serviceItemMapper;
        this.serviceTechnicianMapper = serviceTechnicianMapper;
        this.technicianMapper = technicianMapper;
        this.shiftMapper = shiftMapper;
        this.reservationMapper = reservationMapper;
        this.jsonSupport = jsonSupport;
    }

    public static boolean isAuto(Long technicianId) {
        return technicianId == null || technicianId <= 0;
    }

    public static void validateBookableDate(RuleConfig rule, LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(rule.getBookableDays() - 1L))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "日期超出可预约范围");
        }
    }

    public ServiceItem requireService(long serviceId) {
        ServiceItem item = serviceItemMapper.selectById(serviceId);
        if (item == null || item.getStatus() != ServiceItem.STATUS_ON) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "服务不存在或已下架");
        }
        return item;
    }

    public ServiceItem service(long serviceId) {
        return serviceItemMapper.selectById(serviceId);
    }

    public Technician technician(long technicianId) {
        return technicianMapper.selectById(technicianId);
    }

    public RuleConfig rules() {
        RuleConfig rule = ruleConfigMapper.selectById(AppConstants.STORE_ID);
        if (rule == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "预约规则未初始化");
        }
        return rule;
    }

    public List<TimeRange> businessHours(LocalDate date) {
        Store store = storeMapper.selectById(AppConstants.STORE_ID);
        if (store == null) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "门店不存在");
        }
        Map<String, List<TimeRange>> hours = jsonSupport.read(store.getBusinessHours(), new TypeReference<>() {
        });
        if (hours == null) {
            return List.of();
        }
        return hours.getOrDefault(String.valueOf(date.getDayOfWeek().getValue()), List.of());
    }

    /**
     * 可约技师：显式指定时校验其确实提供该服务且启用；不指定时返回全部可约技师。
     */
    public List<Technician> technicians(long serviceId, Long technicianId) {
        List<ServiceTechnician> relations = serviceTechnicianMapper.selectList(
                new LambdaQueryWrapper<ServiceTechnician>().eq(ServiceTechnician::getServiceId, serviceId));
        Set<Long> technicianIds = relations.stream().map(ServiceTechnician::getTechnicianId).collect(Collectors.toSet());
        if (!isAuto(technicianId)) {
            if (!technicianIds.contains(technicianId)) {
                throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "该技师不提供此服务");
            }
            Technician technician = technicianMapper.selectById(technicianId);
            if (technician == null || technician.getStatus() != Technician.STATUS_ENABLED) {
                throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "技师不可约");
            }
            return List.of(technician);
        }
        if (technicianIds.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "该服务暂无可约技师");
        }
        List<Technician> technicians = technicianMapper.selectByIds(technicianIds).stream()
                .filter(technician -> technician.getStatus() == Technician.STATUS_ENABLED)
                .sorted(Comparator.comparingLong(Technician::getId))
                .toList();
        if (technicians.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "该服务暂无可约技师");
        }
        return technicians;
    }

    public Map<Long, List<TimeRange>> shiftIntervals(Collection<Long> technicianIds, LocalDate date) {
        if (technicianIds.isEmpty()) {
            return Map.of();
        }
        int weekday = date.getDayOfWeek().getValue();
        List<Shift> shifts = shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                .in(Shift::getTechnicianId, technicianIds)
                .and(wrapper -> wrapper.eq(Shift::getScope, Shift.SCOPE_DATE).eq(Shift::getDate, date)
                        .or(nested -> nested.eq(Shift::getScope, Shift.SCOPE_WEEK).eq(Shift::getWeekday, weekday))));
        Map<Long, Shift> overrides = new HashMap<>();
        Map<Long, Shift> templates = new HashMap<>();
        for (Shift shift : shifts) {
            if (Shift.SCOPE_DATE.equals(shift.getScope())) {
                overrides.putIfAbsent(shift.getTechnicianId(), shift);
            } else if (Shift.SCOPE_WEEK.equals(shift.getScope())) {
                templates.putIfAbsent(shift.getTechnicianId(), shift);
            }
        }
        Map<Long, List<TimeRange>> result = new HashMap<>();
        for (Long technicianId : technicianIds) {
            Shift effective = overrides.getOrDefault(technicianId, templates.get(technicianId));
            if (effective == null || !Shift.TYPE_WORK.equals(effective.getType())) {
                result.put(technicianId, List.of());
            } else {
                List<TimeRange> intervals = jsonSupport.read(effective.getIntervals(), new TypeReference<>() {
                });
                result.put(technicianId, intervals == null ? List.of() : intervals);
            }
        }
        return result;
    }

    public List<Reservation> activeReservations(LocalDate date) {
        List<Reservation> reservations = reservationMapper.selectActiveByDate(AppConstants.STORE_ID, date);
        return reservations == null ? new ArrayList<>() : reservations;
    }

    public Map<Long, ServiceItem> services(Collection<Long> serviceIds) {
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        return serviceItemMapper.selectByIds(serviceIds).stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity(), (a, b) -> a));
    }

    public Map<Long, Technician> technicians(Collection<Long> technicianIds) {
        if (technicianIds.isEmpty()) {
            return Map.of();
        }
        return technicianMapper.selectByIds(technicianIds).stream()
                .collect(Collectors.toMap(Technician::getId, Function.identity(), (a, b) -> a));
    }
}
