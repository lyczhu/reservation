package com.lawyus.reservation.beauty.dashboard;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.beauty.dashboard.dto.DashboardBlockVO;
import com.lawyus.reservation.beauty.dashboard.dto.DashboardStatsVO;
import com.lawyus.reservation.beauty.dashboard.dto.DashboardTechnicianVO;
import com.lawyus.reservation.beauty.dashboard.dto.DashboardVO;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.customer.booking.BookingDataService;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.reservation.ReservationTexts;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import com.lawyus.reservation.kernel.slot.SlotCalculator;

/**
 * 今日看板：按营业时段与粒度生成时间轴，技师为纵轴输出预约色块与统计；
 * 技师角色仅返回本人行与本人统计，预计营收只计待服务与已完成单。
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_DAY_OFFSET = 7;

    private final ReservationMapper reservationMapper;
    private final TechnicianMapper technicianMapper;
    private final BookingDataService data;

    public DashboardService(ReservationMapper reservationMapper,
                            TechnicianMapper technicianMapper,
                            BookingDataService data) {
        this.reservationMapper = reservationMapper;
        this.technicianMapper = technicianMapper;
        this.data = data;
    }

    public DashboardVO get(LocalDate date, boolean owner, Long selfTechnicianId) {
        LocalDate target = date == null ? LocalDate.now() : date;
        if (Math.abs(ChronoUnit.DAYS.between(LocalDate.now(), target)) > MAX_DAY_OFFSET) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "日期仅支持前后 7 天");
        }
        if (!owner && selfTechnicianId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ROLE, "技师账号未绑定技师档案");
        }

        RuleConfig rule = data.rules();
        List<TimeRange> businessHours = data.businessHours(target);
        List<Reservation> reservations = reservationMapper.selectByDate(AppConstants.STORE_ID, target);
        if (!owner) {
            reservations = reservations.stream()
                    .filter(reservation -> selfTechnicianId.equals(reservation.getTechnicianId()))
                    .toList();
        }

        Map<Long, ServiceItem> services = data.services(
                reservations.stream().map(Reservation::getServiceId).collect(Collectors.toSet()));
        Map<Long, List<TimeRange>> shifts = data.shiftIntervals(
                reservations.stream().map(Reservation::getTechnicianId).collect(Collectors.toSet()), target);
        List<DashboardBlockVO> blocks = reservations.stream()
                .map(reservation -> {
                    ServiceItem service = services.get(reservation.getServiceId());
                    return new DashboardBlockVO(
                            reservation.getId(),
                            reservation.getNo(),
                            reservation.getTechnicianId(),
                            service == null ? "" : service.getName(),
                            reservation.getStartTime().format(HH_MM),
                            reservation.getEndTime().format(HH_MM),
                            reservation.getStatus(),
                            ReservationTexts.status(reservation.getStatus()),
                            conflicts(reservation, shifts.getOrDefault(reservation.getTechnicianId(), List.of())));
                })
                .toList();

        int waitConfirm = 0;
        int waitService = 0;
        int completed = 0;
        int revenueCents = 0;
        for (Reservation reservation : reservations) {
            switch (reservation.getStatus()) {
                case Reservation.STATUS_WAIT_CONFIRM -> waitConfirm++;
                case Reservation.STATUS_WAIT_SERVICE -> waitService++;
                case Reservation.STATUS_COMPLETED -> completed++;
                default -> {
                }
            }
            if (reservation.getStatus() == Reservation.STATUS_WAIT_SERVICE
                    || reservation.getStatus() == Reservation.STATUS_COMPLETED) {
                ServiceItem service = services.get(reservation.getServiceId());
                if (service != null) {
                    revenueCents += service.getPriceCents();
                }
            }
        }

        List<Technician> technicians = technicianMapper.selectList(new LambdaQueryWrapper<Technician>()
                .orderByAsc(Technician::getId));
        if (!owner) {
            technicians = technicians.stream()
                    .filter(technician -> technician.getId().equals(selfTechnicianId))
                    .toList();
        }

        return new DashboardVO(
                target,
                businessHours,
                slotStarts(businessHours, rule.getSlotGranularity()),
                technicians.stream()
                        .map(technician -> new DashboardTechnicianVO(
                                technician.getId(), technician.getName(), technician.getAvatar()))
                        .toList(),
                blocks,
                new DashboardStatsVO(reservations.size(), waitConfirm, waitService, completed, revenueCents));
    }

    /**
     * 仅活跃单参与冲突判定：技师当前排班已不覆盖预约时段时标红，终态单不再需要处理。
     */
    private boolean conflicts(Reservation reservation, List<TimeRange> shiftIntervals) {
        if (reservation.getStatus() != Reservation.STATUS_WAIT_CONFIRM
                && reservation.getStatus() != Reservation.STATUS_WAIT_SERVICE) {
            return false;
        }
        return !SlotCalculator.covers(shiftIntervals, reservation.getStartTime(), reservation.getEndTime());
    }

    private List<String> slotStarts(List<TimeRange> businessHours, int granularity) {
        List<String> starts = new ArrayList<>();
        for (TimeRange window : businessHours) {
            LocalTime start = LocalTime.parse(window.getStart());
            LocalTime end = LocalTime.parse(window.getEnd());
            while (start.isBefore(end)) {
                starts.add(start.format(HH_MM));
                start = start.plusMinutes(granularity);
            }
        }
        return starts;
    }
}
