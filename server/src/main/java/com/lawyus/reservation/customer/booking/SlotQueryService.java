package com.lawyus.reservation.customer.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.customer.booking.dto.SlotResultVO;
import com.lawyus.reservation.customer.booking.dto.SlotVO;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.slot.Slot;
import com.lawyus.reservation.kernel.slot.SlotCalculator;
import com.lawyus.reservation.kernel.slot.SlotState;

/**
 * 时段查询：指定技师按其班次计算；不指定技师时取所有可约技师结果的并集。
 */
@Service
public class SlotQueryService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingDataService data;

    public SlotQueryService(BookingDataService data) {
        this.data = data;
    }

    public SlotResultVO query(long serviceId, Long technicianId, LocalDate date) {
        ServiceItem service = data.requireService(serviceId);
        RuleConfig rule = data.rules();
        BookingDataService.validateBookableDate(rule, date);
        List<Technician> technicians = data.technicians(serviceId, technicianId);
        List<TimeRange> businessHours = data.businessHours(date);
        List<Reservation> active = data.activeReservations(date);
        Map<Long, List<TimeRange>> shifts = data.shiftIntervals(
                technicians.stream().map(Technician::getId).toList(), date);

        List<Slot> slots;
        if (BookingDataService.isAuto(technicianId)) {
            slots = null;
            for (Technician technician : technicians) {
                List<Slot> current = calculate(service, rule, businessHours, shifts.get(technician.getId()),
                        active, technician.getId(), date);
                slots = slots == null ? current : merge(slots, current);
            }
            slots = slots == null ? List.of() : slots;
        } else {
            Technician technician = technicians.getFirst();
            slots = calculate(service, rule, businessHours, shifts.get(technician.getId()),
                    active, technician.getId(), date);
        }
        return new SlotResultVO(service.getId(), date, service.getDurationMin(), rule.getSlotGranularity(),
                rule.getMinLeadMinutes(), toVO(slots));
    }

    private List<Slot> calculate(ServiceItem service, RuleConfig rule, List<TimeRange> businessHours,
                                 List<TimeRange> shiftIntervals, List<Reservation> active,
                                 long technicianId, LocalDate date) {
        List<SlotCalculator.BusyRange> busy = active.stream()
                .filter(reservation -> reservation.getTechnicianId() != null
                        && reservation.getTechnicianId() == technicianId)
                .map(reservation -> new SlotCalculator.BusyRange(
                        reservation.getStartTime(), reservation.getEndTime()))
                .toList();
        return SlotCalculator.calculate(businessHours, shiftIntervals, busy, service.getDurationMin(),
                rule.getBufferMinutes(), rule.getSlotGranularity(), date, LocalDateTime.now(),
                rule.getMinLeadMinutes());
    }

    private List<Slot> merge(List<Slot> left, List<Slot> right) {
        List<Slot> merged = new ArrayList<>(left.size());
        for (int i = 0; i < left.size(); i++) {
            Slot slot = left.get(i);
            SlotState state = i < right.size()
                    ? SlotCalculator.merge(List.of(slot.state(), right.get(i).state()))
                    : slot.state();
            merged.add(new Slot(slot.start(), slot.end(), state));
        }
        return merged;
    }

    private List<SlotVO> toVO(List<Slot> slots) {
        return slots.stream()
                .map(slot -> new SlotVO(slot.start().format(HH_MM), slot.end().format(HH_MM), slot.state().getCode()))
                .toList();
    }
}
